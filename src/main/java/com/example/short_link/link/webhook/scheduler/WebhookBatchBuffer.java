package com.example.short_link.link.webhook.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebhookBatchBuffer {

  private static final int BATCH_MAX_EVENTS_PER_FLUSH = 50;
  private static final int BATCH_QUEUE_HARD_CAP = 500;

  private final MeterRegistry meterRegistry;
  private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Map<String, Object>>> queues =
      new ConcurrentHashMap<>();

  void enqueue(Long hookId, Map<String, Object> payload) {
    ConcurrentLinkedQueue<Map<String, Object>> queue =
        queues.computeIfAbsent(hookId, k -> new ConcurrentLinkedQueue<>());
    if (queue.size() >= BATCH_QUEUE_HARD_CAP) {
      meterRegistry.counter("webhook.delivery", "result", "dropped_overflow").increment();
      return;
    }
    queue.add(payload);
  }

  boolean isEmpty(Long hookId) {
    ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(hookId);
    return queue == null || queue.isEmpty();
  }

  List<Map<String, Object>> drain(Long hookId) {
    ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(hookId);
    if (queue == null || queue.isEmpty()) return List.of();
    List<Map<String, Object>> drained = new ArrayList<>();
    Map<String, Object> next;
    while (drained.size() < BATCH_MAX_EVENTS_PER_FLUSH && (next = queue.poll()) != null) {
      drained.add(next);
    }
    return drained;
  }

  void clear(Long hookId) {
    ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(hookId);
    if (queue != null) queue.clear();
  }

  List<Long> hookIds() {
    return List.copyOf(queues.keySet());
  }

  int size(Long hookId) {
    ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(hookId);
    return queue == null ? 0 : queue.size();
  }
}
