package com.example.short_link.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues metrics without a database write on the request thread. Overflow is dropped and counted to
 * bound memory during bursts or outages; each flush drains at most {@link #MAX_BATCH_PER_FLUSH}
 * rows.
 */
@Slf4j
@Component
public class RequestMetricsRecorder {

  static final int MAX_BATCH_PER_FLUSH = 500;
  static final int QUEUE_HARD_CAP = 50_000;

  private final RequestMetricJpaRepository repository;
  private final MeterRegistry meterRegistry;
  private final ConcurrentLinkedQueue<RequestMetric> queue = new ConcurrentLinkedQueue<>();

  public RequestMetricsRecorder(
      RequestMetricJpaRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
  }

  public void record(RequestMetric metric) {
    if (queue.size() >= QUEUE_HARD_CAP) {
      meterRegistry.counter("request_metrics.recorder", "result", "dropped_overflow").increment();
      return;
    }
    queue.add(metric);
  }

  @Scheduled(fixedDelay = 1000)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void flush() {
    List<RequestMetricEntity> batch = new ArrayList<>(MAX_BATCH_PER_FLUSH);
    RequestMetric next;
    while (batch.size() < MAX_BATCH_PER_FLUSH && (next = queue.poll()) != null) {
      batch.add(new RequestMetricEntity(next));
    }
    if (batch.isEmpty()) return;
    try {
      repository.saveAll(batch);
      meterRegistry
          .counter("request_metrics.recorder", "result", "flushed")
          .increment(batch.size());
    } catch (Exception e) {
      // Best-effort telemetry: polled rows are lost on failure and counted rather than retried.
      meterRegistry
          .counter("request_metrics.recorder", "result", "flush_error")
          .increment(batch.size());
      log.warn("request_metrics flush failed; dropped {} rows: {}", batch.size(), e.toString());
    }
  }

  int queueSize() {
    return queue.size();
  }
}
