package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RequestMetricsRecorderTest {

  private final RequestMetricRepository repository = mock(RequestMetricRepository.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final RequestMetricsRecorder recorder = new RequestMetricsRecorder(repository, registry);

  @Test
  void recordEnqueuesAndFlushDrainsToRepository() {
    recorder.record(sample("GET /r/{shortCode}", 302, "redirect", 5));
    recorder.record(sample("GET /api/links", 200, "ok", 12));
    assertThat(recorder.queueSize()).isEqualTo(2);

    recorder.flush();

    verify(repository, times(1)).saveAll(anyIterable());
    assertThat(recorder.queueSize()).isZero();
    assertThat(registry.counter("request_metrics.recorder", "result", "flushed").count())
        .isEqualTo(2.0);
  }

  @Test
  void flushIsNoOpWhenQueueEmpty() {
    recorder.flush();
    verify(repository, never()).saveAll(anyIterable());
  }

  @Test
  void overflowDropsRowsAndIncrementsCounter() {
    // Saturating the queue without a flush — the cap is internal but we can fill via direct adds
    // through the public record() API. Burns one second of CPU at most because of the early
    // overflow short-circuit, so a tight loop is fine.
    for (int i = 0; i < RequestMetricsRecorder.QUEUE_HARD_CAP + 10; i++) {
      recorder.record(sample("GET /api/spam", 200, "ok", 1));
    }
    assertThat(recorder.queueSize()).isLessThanOrEqualTo(RequestMetricsRecorder.QUEUE_HARD_CAP);
    assertThat(registry.counter("request_metrics.recorder", "result", "dropped_overflow").count())
        .isGreaterThanOrEqualTo(10.0);
  }

  @Test
  void flushFailureCountsButDoesNotRethrow() {
    when(repository.saveAll(anyIterable()))
        .thenThrow(new RuntimeException("DB down"))
        .thenReturn(java.util.List.of());

    recorder.record(sample("GET /api/links", 500, "error", 3));
    recorder.flush(); // first call throws inside saveAll — must not propagate

    assertThat(registry.counter("request_metrics.recorder", "result", "flush_error").count())
        .isEqualTo(1.0);
  }

  private static RequestMetric sample(String route, int status, String outcome, long latency) {
    String[] parts = route.split(" ", 2);
    return new RequestMetric(
        Instant.parse("2026-05-19T10:00:00Z"),
        parts[1],
        parts[0],
        status,
        outcome,
        latency,
        null,
        null,
        null);
  }
}
