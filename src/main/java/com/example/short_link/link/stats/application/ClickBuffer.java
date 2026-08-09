package com.example.short_link.link.stats.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Bounded FIFO holding clicks between the request thread and the flusher. Size is tracked in a
 * separate counter because {@link ConcurrentLinkedQueue#size()} is O(n) — the request path cannot
 * afford to walk the queue on every click.
 */
@Component
public class ClickBuffer {

  private final ConcurrentLinkedQueue<PendingClick> queue = new ConcurrentLinkedQueue<>();
  private final AtomicInteger size = new AtomicInteger();
  private final int capacity;

  public ClickBuffer(ClickRecorderProperties properties) {
    this.capacity = properties.queueCapacity();
  }

  public boolean offer(PendingClick click) {
    if (size.get() >= capacity) {
      return false;
    }
    queue.add(click);
    size.incrementAndGet();
    return true;
  }

  public List<PendingClick> drain(int max) {
    List<PendingClick> drained = new ArrayList<>(Math.min(max, 64));
    PendingClick next;
    while (drained.size() < max && (next = queue.poll()) != null) {
      size.decrementAndGet();
      drained.add(next);
    }
    return drained;
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  public int size() {
    return size.get();
  }
}
