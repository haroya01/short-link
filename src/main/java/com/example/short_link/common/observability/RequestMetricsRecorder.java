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
 * Buffered async writer for {@link RequestMetric}. The hot path ({@code RequestMetricsFilter}) does
 * one queue offer per request — no DB hit, no lock contention on the request thread. A {@link
 * Scheduled} tick drains up to {@link #MAX_BATCH_PER_FLUSH} rows per second into a single {@code
 * saveAll} insert.
 *
 * <p>At 100k req/day average the queue effectively never fills; the {@link #QUEUE_HARD_CAP} exists
 * only so a pathological burst (DB outage during a click spike) can't grow unbounded — past the cap
 * we drop with a counter so we have evidence it happened rather than OOMing the JVM.
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
      // Recorder is best-effort observability — never let a flush failure break the app. The
      // dropped rows are gone (we already polled them off the queue), so log enough detail to
      // diagnose without keeping the data around.
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
