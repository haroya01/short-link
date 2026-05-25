package com.example.short_link.admin.application;

import com.example.short_link.admin.application.dto.AdminRouteMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricRepository;
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
 * Replaces the in-process {@code RouteMetricsRingBuffer} that used to drive these endpoints — the
 * ring sampled Micrometer's {@code http.server.requests} timer once a minute, which gave us
 * per-minute deltas but no raw events to drill into. The new source carries every request, so
 * percentile and error-rate computations operate over the actual sample set rather than the timer's
 * bucketed approximation.
 */
@Service
public class AdminRouteMetricsService {

  /** Hard cap for lifetime aggregates — beyond this we'd be sorting hundreds of MB in heap. */
  private static final Duration LIFETIME_CAP = Duration.ofDays(7);

  private final RequestMetricRepository repository;
  private final Clock clock;

  @Autowired
  public AdminRouteMetricsService(RequestMetricRepository repository) {
    this(repository, Clock.systemUTC());
  }

  AdminRouteMetricsService(RequestMetricRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public List<AdminRouteMetric> routeMetrics() {
    return aggregate(LIFETIME_CAP);
  }

  public List<AdminRouteMetric> routeMetricsWindow(Window window) {
    return aggregate(window.duration());
  }

  private List<AdminRouteMetric> aggregate(Duration window) {
    Instant now = clock.instant();
    Instant from = now.minus(window);
    List<RequestMetricEntity> rows = repository.findWindow(from, now);
    Map<String, List<RequestMetricEntity>> byRoute = new HashMap<>();
    for (RequestMetricEntity row : rows) {
      byRoute
          .computeIfAbsent(row.getMethod() + " " + row.getRoute(), k -> new ArrayList<>())
          .add(row);
    }
    List<AdminRouteMetric> out = new ArrayList<>(byRoute.size());
    for (Map.Entry<String, List<RequestMetricEntity>> entry : byRoute.entrySet()) {
      List<RequestMetricEntity> group = entry.getValue();
      long count = group.size();
      long error5xx = 0;
      long[] latencies = new long[group.size()];
      Map<String, Long> statusDist = new TreeMap<>();
      for (int i = 0; i < group.size(); i++) {
        RequestMetricEntity row = group.get(i);
        latencies[i] = row.getLatencyMs();
        if (row.getStatus() >= 500) error5xx++;
        statusDist.merge(String.valueOf(row.getStatus()), 1L, Long::sum);
      }
      java.util.Arrays.sort(latencies);
      RequestMetricEntity first = group.get(0);
      double errorRate = count == 0 ? 0.0 : (double) error5xx / (double) count;
      out.add(
          new AdminRouteMetric(
              first.getRoute(),
              first.getMethod(),
              count,
              percentile(latencies, 0.5),
              percentile(latencies, 0.95),
              percentile(latencies, 0.99),
              errorRate,
              error5xx,
              sortStatusDistribution(statusDist)));
    }
    out.sort(Comparator.comparingLong(AdminRouteMetric::count).reversed());
    return out;
  }

  private static double percentile(long[] sorted, double p) {
    if (sorted.length == 0) return 0.0;
    if (sorted.length == 1) return sorted[0];
    double rank = (sorted.length - 1) * p;
    int lo = (int) Math.floor(rank);
    int hi = (int) Math.ceil(rank);
    if (lo == hi) return sorted[lo];
    return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
  }

  private static Map<String, Long> sortStatusDistribution(Map<String, Long> src) {
    // Status codes sort lexicographically (200 < 404 < 500) — meaningful enough for a debug panel
    // and stable across renders.
    Map<String, Long> sorted = new LinkedHashMap<>();
    new TreeMap<>(src).forEach(sorted::put);
    return sorted;
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
}
