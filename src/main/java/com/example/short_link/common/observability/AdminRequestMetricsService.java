package com.example.short_link.common.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Reads aggregates out of {@code request_metrics}. Three views match the dashboards we actually
 * need:
 *
 * <ul>
 *   <li>{@link #routes(Window)} — per-route count / p50 / p95 / p99 / error rate, replacing the
 *       per-minute snapshot ring (one PR ahead the ring will go away).
 *   <li>{@link #outcomes(String, Window)} — per-{@code shortCode} outcome distribution so the
 *       link-metrics drilldown can show the redirect / not_found / expired / blocked split.
 *   <li>{@link #raw(RawQuery)} — paged raw rows for incident drill-downs. Filters compose freely.
 * </ul>
 *
 * <p>Percentiles are computed in-process from the windowed slice. At 100k req/day a 7-day window is
 * ~700k rows; the indexed range scan keeps the read cheap, but the in-memory sort would not scale
 * past that, so {@link Window} caps the window length and {@link RawQuery} caps the page size.
 */
@Service
public class AdminRequestMetricsService {

  private static final int RAW_MAX_LIMIT = 500;

  private final RequestMetricRepository repository;
  private final Clock clock;

  @Autowired
  public AdminRequestMetricsService(RequestMetricRepository repository) {
    this(repository, Clock.systemUTC());
  }

  AdminRequestMetricsService(RequestMetricRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<RouteAggregate> routes(Window window) {
    Instant now = clock.instant();
    Instant from = now.minus(window.duration());
    List<RequestMetricEntity> rows = repository.findWindow(from, now);
    Map<String, List<RequestMetricEntity>> byRoute = new HashMap<>();
    for (RequestMetricEntity row : rows) {
      byRoute
          .computeIfAbsent(row.getMethod() + " " + row.getRoute(), k -> new ArrayList<>())
          .add(row);
    }
    List<RouteAggregate> out = new ArrayList<>(byRoute.size());
    for (Map.Entry<String, List<RequestMetricEntity>> entry : byRoute.entrySet()) {
      List<RequestMetricEntity> group = entry.getValue();
      long count = group.size();
      long errors = 0;
      long[] latencies = new long[group.size()];
      Map<String, Long> statusDist = new TreeMap<>();
      Map<String, Long> outcomeDist = new TreeMap<>();
      for (int i = 0; i < group.size(); i++) {
        RequestMetricEntity row = group.get(i);
        latencies[i] = row.getLatencyMs();
        if (row.getStatus() >= 500) errors++;
        statusDist.merge(String.valueOf(row.getStatus()), 1L, Long::sum);
        outcomeDist.merge(row.getOutcome(), 1L, Long::sum);
      }
      java.util.Arrays.sort(latencies);
      RequestMetricEntity first = group.get(0);
      out.add(
          new RouteAggregate(
              first.getMethod(),
              first.getRoute(),
              count,
              percentile(latencies, 0.5),
              percentile(latencies, 0.95),
              percentile(latencies, 0.99),
              count == 0 ? 0.0 : (double) errors / (double) count,
              statusDist,
              outcomeDist));
    }
    out.sort(Comparator.comparingLong(RouteAggregate::count).reversed());
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

  /**
   * Linear-interpolation percentile over a pre-sorted long array. Standard {@code (n-1) * p}
   * indexing, matching what Micrometer's percentile snapshot does — keeps the new endpoint in the
   * same ballpark as the old ring snapshot so dashboards don't appear to "jump" on switchover.
   */
  static double percentile(long[] sorted, double p) {
    if (sorted.length == 0) return 0.0;
    if (sorted.length == 1) return sorted[0];
    double rank = (sorted.length - 1) * p;
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) return sorted[lo];
    return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
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
