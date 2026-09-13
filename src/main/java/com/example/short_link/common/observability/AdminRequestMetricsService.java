package com.example.short_link.common.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Percentiles sort the window's rows in memory; {@link Window} limits that workload and {@link
 * RawQuery} caps the raw page size.
 */
@Service
public class AdminRequestMetricsService {

  private static final int RAW_MAX_LIMIT = 500;

  private final RequestMetricJpaRepository repository;
  private final Clock clock;

  public AdminRequestMetricsService(RequestMetricJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<RouteAggregate> routes(Window window) {
    Instant now = clock.instant();
    Instant from = now.minus(window.duration());
    List<RequestMetricEntity> rows = repository.findWindow(from, now);
    List<RouteAggregate> out = new ArrayList<>();
    for (RequestRouteMetrics.Summary summary : RequestRouteMetrics.summarizeWithOutcomes(rows)) {
      out.add(
          new RouteAggregate(
              summary.method(),
              summary.route(),
              summary.count(),
              summary.p50(),
              summary.p95(),
              summary.p99(),
              summary.errorRate(),
              summary.statusDistribution(),
              summary.outcomeDistribution()));
    }
    return out;
  }

  public OutcomeDistribution outcomes(String shortCode, Window window) {
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
    Instant now = clock.instant();
    Instant from = now.minus(window.duration());
    List<RequestMetricEntity> rows = repository.findShortCodeWindow(shortCode, from, now);
    Map<String, Long> dist = new LinkedHashMap<>();
    for (RequestMetricEntity row : rows) {
      dist.merge(row.getOutcome(), 1L, Long::sum);
    }
    return new OutcomeDistribution(shortCode, rows.size(), dist);
  }

  public List<RawRow> raw(RawQuery query) {
    Instant to = query.to() != null ? query.to() : clock.instant();
    Instant from = query.from() != null ? query.from() : to.minus(Duration.ofHours(1));
    int limit = query.limit() == null ? 100 : Math.min(query.limit(), RAW_MAX_LIMIT);
    List<RequestMetricEntity> rows = repository.findWindow(from, to);
    List<RawRow> out = new ArrayList<>();
    for (RequestMetricEntity row : rows) {
      if (query.route() != null && !query.route().equals(row.getRoute())) continue;
      if (query.outcome() != null && !query.outcome().equals(row.getOutcome())) continue;
      if (query.shortCode() != null && !query.shortCode().equals(row.getShortCode())) continue;
      if (query.userId() != null && !query.userId().equals(row.getUserId())) continue;
      out.add(
          new RawRow(
              row.getOccurredAt(),
              row.getRoute(),
              row.getMethod(),
              row.getStatus(),
              row.getOutcome(),
              row.getLatencyMs(),
              row.getShortCode(),
              row.getUserId(),
              row.getTraceId()));
      if (out.size() >= limit) break;
    }
    return out;
  }

  public enum Window {
    H1(Duration.ofHours(1)),
    H24(Duration.ofHours(24)),
    D7(Duration.ofDays(7));

    private final Duration duration;

    Window(Duration duration) {
      this.duration = duration;
    }

    public Duration duration() {
      return duration;
    }

    public static Window parse(String raw) {
      if (raw == null) return H1;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "1h", "h1" -> H1;
        case "24h", "h24", "1d", "d1" -> H24;
        case "7d", "d7", "week" -> D7;
        default -> H1;
      };
    }
  }

  public record RouteAggregate(
      String method,
      String route,
      long count,
      double p50,
      double p95,
      double p99,
      double errorRate,
      Map<String, Long> statusDistribution,
      Map<String, Long> outcomeDistribution) {}

  public record OutcomeDistribution(String shortCode, long total, Map<String, Long> outcomes) {}

  public record RawRow(
      Instant occurredAt,
      String route,
      String method,
      int status,
      String outcome,
      long latencyMs,
      String shortCode,
      Long userId,
      String traceId) {}

  public record RawQuery(
      Instant from,
      Instant to,
      String route,
      String outcome,
      String shortCode,
      Long userId,
      Integer limit) {}
}
