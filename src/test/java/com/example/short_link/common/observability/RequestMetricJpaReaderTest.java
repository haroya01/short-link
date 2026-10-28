package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestMetricJpaReaderTest {

  private static final Instant FROM = Instant.parse("2026-10-27T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-27T01:00:00Z");

  @Test
  void rowsComeBackAsRecordsWithEveryFieldInTheRepositorysOrder() {
    RequestMetric redirect =
        new RequestMetric(
            Instant.parse("2026-10-27T00:10:00Z"),
            "/r/{shortCode}",
            "GET",
            302,
            "redirect",
            12,
            "abc123",
            7L,
            "trace-1");
    RequestMetric create =
        new RequestMetric(
            Instant.parse("2026-10-27T00:20:00Z"),
            "/api/links",
            "POST",
            201,
            "ok",
            40,
            null,
            null,
            "trace-2");
    RequestMetricJpaRepository repository = mock(RequestMetricJpaRepository.class);
    when(repository.findWindow(FROM, TO))
        .thenReturn(List.of(new RequestMetricEntity(redirect), new RequestMetricEntity(create)));
    when(repository.findShortCodeWindow("abc123", FROM, TO))
        .thenReturn(List.of(new RequestMetricEntity(redirect)));
    RequestMetricReader reader = new RequestMetricJpaReader(repository);

    assertThat(reader.between(FROM, TO)).containsExactly(redirect, create);
    assertThat(reader.forShortCodeBetween("abc123", FROM, TO)).containsExactly(redirect);
  }
}
