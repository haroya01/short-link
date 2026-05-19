package com.example.short_link.admin.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Aggregates {@code http.server.requests} timers by {@code (uri, method)} pair. The actuator
 * histogram registers a separate timer per (uri, method, status, outcome) tuple, so several timers
 * may belong to the same route — counts are summed and percentiles are taken as the worst (max)
 * observed across status partitions so a single slow 500 doesn't get washed out by fast 2xx
 * samples. Error rate is the fraction of {@code status >= 500} samples.
 */
@Service
@RequiredArgsConstructor
public class AdminRouteMetricsService {

  private static final String HTTP_TIMER = "http.server.requests";

  private final MeterRegistry meterRegistry;

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
              a.uri, a.method, a.count, a.p50, a.p95, a.p99, errorRate, a.error5xx));
    }
    out.sort(Comparator.comparingLong(AdminRouteMetric::count).reversed());
    return out;
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

    Accumulator(String uri, String method) {
      this.uri = uri;
      this.method = method;
    }
  }
}
