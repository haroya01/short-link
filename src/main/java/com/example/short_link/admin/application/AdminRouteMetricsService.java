package com.example.short_link.admin.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Aggregates {@code http.server.requests} timers by {@code (uri, method)} pair. The actuator
 * histogram registers a separate timer per (uri, method, status, outcome) tuple, so several timers
 * may belong to the same route — counts are summed and percentiles are taken as the worst (max)
 * observed across status partitions so a single slow 500 doesn't get washed out by fast 2xx
 * samples. Error rate is the fraction of {@code status >= 500} samples.
 *
 * <p>The aggregate {@link #routeMetrics()} reports everything since process start. {@link
 * #routeMetricsWindow(Window)} returns the same shape sliced to a recent time window backed by
 * {@link RouteMetricsRingBuffer}'s per-minute snapshots.
 */
@Service
@RequiredArgsConstructor
public class AdminRouteMetricsService {

  private static final String HTTP_TIMER = "http.server.requests";

  private final MeterRegistry meterRegistry;
  private final RouteMetricsRingBuffer ring;

  public List<AdminRouteMetric> routeMetrics() {
    Map<String, Accumulator> byRoute = new HashMap<>();
    for (Timer t : meterRegistry.find(HTTP_TIMER).timers()) {
      String uri = t.getId().getTag("uri");
      String method = t.getId().getTag("method");
      String status = t.getId().getTag("status");
      if (uri == null || method == null) continue;
      String key = method + " " + uri;
      Accumulator acc = byRoute.computeIfAbsent(key, k -> new Accumulator(uri, method));
      HistogramSnapshot snap = t.takeSnapshot();
      long sampleCount = snap.count();
      acc.count += sampleCount;
      if (status != null && !status.isEmpty()) {
        char first = status.charAt(0);
        if (first == '5') acc.error5xx += sampleCount;
        acc.statusDistribution.merge(status, sampleCount, Long::sum);
      }
      for (ValueAtPercentile v : snap.percentileValues()) {
        double ms = v.value(TimeUnit.MILLISECONDS);
        if (closeTo(v.percentile(), 0.5)) acc.p50 = Math.max(acc.p50, ms);
        else if (closeTo(v.percentile(), 0.95)) acc.p95 = Math.max(acc.p95, ms);
        else if (closeTo(v.percentile(), 0.99)) acc.p99 = Math.max(acc.p99, ms);
      }
    }

    List<AdminRouteMetric> out = new ArrayList<>(byRoute.size());
    for (Accumulator a : byRoute.values()) {
      double errorRate = a.count == 0 ? 0.0 : (double) a.error5xx / (double) a.count;
      out.add(
          new AdminRouteMetric(
              a.uri,
              a.method,
              a.count,
              a.p50,
              a.p95,
              a.p99,
              errorRate,
              a.error5xx,
              sortStatusDistribution(a.statusDistribution)));
    }
    out.sort(Comparator.comparingLong(AdminRouteMetric::count).reversed());
    return out;
  }

  /**
   * Time-windowed slice. Counts are summed across the buckets in the window; p95/p99 are the worst
   * minute observed within it. Status distribution is also per-status sum within the window — that
   * lets the UI show "in the last hour this route was 80% 200 / 18% 404 / 2% 500".
   */
  public List<AdminRouteMetric> routeMetricsWindow(Window window) {
    Map<RouteMetricsRingBuffer.RouteKey, List<RouteMetricsRingBuffer.TupleSlice>> slice =
        ring.sliceWindow(window.minutes());
    List<AdminRouteMetric> out = new ArrayList<>(slice.size());
    for (var entry : slice.entrySet()) {
      RouteMetricsRingBuffer.RouteKey route = entry.getKey();
      long count = 0;
      long error5xx = 0;
      double p95 = 0;
      double p99 = 0;
      Map<String, Long> dist = new TreeMap<>();
      for (RouteMetricsRingBuffer.TupleSlice s : entry.getValue()) {
        long tupleCount = 0;
        for (RouteMetricsRingBuffer.MinuteBucket b : s.buckets()) {
          tupleCount += b.count();
          p95 = Math.max(p95, b.p95Millis());
          p99 = Math.max(p99, b.p99Millis());
        }
        count += tupleCount;
        if (!s.status().isEmpty() && s.status().charAt(0) == '5') error5xx += tupleCount;
        dist.merge(s.status(), tupleCount, Long::sum);
      }
      if (count == 0) continue;
      double errorRate = (double) error5xx / (double) count;
      out.add(
          new AdminRouteMetric(
              route.uri(),
              route.method(),
              count,
              0.0, // p50 is not retained per-minute (worst-case p95/p99 is what matters here)
              p95,
              p99,
              errorRate,
              error5xx,
              sortStatusDistribution(dist)));
    }
    out.sort(Comparator.comparingLong(AdminRouteMetric::count).reversed());
    return out;
  }

  private static Map<String, Long> sortStatusDistribution(Map<String, Long> src) {
    // Status codes sort lexicographically (200 < 404 < 500) — meaningful enough for a debug panel
    // and stable across renders.
    Map<String, Long> sorted = new LinkedHashMap<>();
    new TreeMap<>(src).forEach(sorted::put);
    return sorted;
  }

  private static boolean closeTo(double a, double b) {
    return Math.abs(a - b) < 1e-6;
  }

  private static final class Accumulator {
    final String uri;
    final String method;
    long count;
    long error5xx;
    double p50;
    double p95;
    double p99;
    final Map<String, Long> statusDistribution = new HashMap<>();

    Accumulator(String uri, String method) {
      this.uri = uri;
      this.method = method;
    }
  }

  public enum Window {
    H1(60),
    H24(60 * 24),
    D7(60 * 24 * 7);

    private final int minutes;

    Window(int minutes) {
      this.minutes = minutes;
    }

    public int minutes() {
      return minutes;
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
