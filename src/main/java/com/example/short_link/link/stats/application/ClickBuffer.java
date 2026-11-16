package com.example.short_link.link.stats.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class ClickBuffer {

  private final ConcurrentLinkedQueue<PendingClick> queue = new ConcurrentLinkedQueue<>();

  // ConcurrentLinkedQueue.size() 는 O(n) — 요청 경로의 용량 검사는 이 카운터로만 한다.
  private final AtomicInteger size = new AtomicInteger();
  private final AtomicLong refused = new AtomicLong();
  private final int capacity;

  public ClickBuffer(ClickRecorderProperties properties) {
    this.capacity = properties.queueCapacity();
  }

  public boolean offer(PendingClick click) {
    if (size.get() >= capacity) {
      refused.incrementAndGet();
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

  public long takeRefused() {
    return refused.getAndSet(0);
  }

  public int capacity() {
    return capacity;
  }
}
