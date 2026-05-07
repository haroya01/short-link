package com.example.short_link.admin.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminHealthService {

  private static final String HTTP_TIMER = "http.server.requests";
  private static final String HIKARI_PREFIX = "hikaricp.connections";
  private static final String CACHE_GETS = "cache.gets";

  private final MeterRegistry meterRegistry;

  public AdminHealthMetrics metrics() {
    return new AdminHealthMetrics(
        latency(),
        statusCounts(),
        counterValue("rate_limit.exceeded"),
        counterValue("safe_browsing.check", Tag.of("result", "malicious")),
        counterValue("auth.failure"),
        dbPool(),
        cache());
  }

  AdminHealthMetrics.HttpLatency latency() {
    double p50 = 0;
    double p95 = 0;
    double p99 = 0;
    long count = 0;
    for (Timer t : meterRegistry.find(HTTP_TIMER).timers()) {
      HistogramSnapshot snap = t.takeSnapshot();
      count += snap.count();
      for (ValueAtPercentile v : snap.percentileValues()) {
        double ms = v.value(java.util.concurrent.TimeUnit.MILLISECONDS);
        if (closeTo(v.percentile(), 0.5)) p50 = Math.max(p50, ms);
        else if (closeTo(v.percentile(), 0.95)) p95 = Math.max(p95, ms);
        else if (closeTo(v.percentile(), 0.99)) p99 = Math.max(p99, ms);
      }
    }
    return new AdminHealthMetrics.HttpLatency(p50, p95, p99, count);
  }

  AdminHealthMetrics.HttpStatusCounts statusCounts() {
    long c2xx = 0;
    long c4xx = 0;
    long c5xx = 0;
    for (Timer t : meterRegistry.find(HTTP_TIMER).timers()) {
      String status = t.getId().getTag("status");
      if (status == null) continue;
      long n = t.count();
      char first = status.charAt(0);
      if (first == '2') c2xx += n;
      else if (first == '4') c4xx += n;
      else if (first == '5') c5xx += n;
    }
    return new AdminHealthMetrics.HttpStatusCounts(c2xx, c4xx, c5xx);
  }

  AdminHealthMetrics.DbPool dbPool() {
    int active = (int) gaugeValue(HIKARI_PREFIX + ".active");
    int idle = (int) gaugeValue(HIKARI_PREFIX + ".idle");
    int waiting = (int) gaugeValue(HIKARI_PREFIX + ".pending");
    int max = (int) gaugeValue(HIKARI_PREFIX + ".max");
    return new AdminHealthMetrics.DbPool(active, idle, waiting, max);
  }

  AdminHealthMetrics.Cache cache() {
    long hits = 0;
    long misses = 0;
    long gets = 0;
    for (Counter c : meterRegistry.find(CACHE_GETS).counters()) {
      String result = c.getId().getTag("result");
      long n = (long) c.count();
      gets += n;
      if ("hit".equals(result)) hits += n;
      else if ("miss".equals(result)) misses += n;
    }
    double ratio = (hits + misses) == 0 ? 0.0 : (double) hits / (hits + misses);
    return new AdminHealthMetrics.Cache(gets, hits, misses, ratio);
  }

  private long counterValue(String name, Tag... tags) {
    double total = 0;
    var search = meterRegistry.find(name);
    if (tags.length > 0) {
      for (Tag t : tags) search = search.tag(t.getKey(), t.getValue());
    }
    for (Counter c : search.counters()) {
      total += c.count();
    }
    return (long) total;
  }

  private double gaugeValue(String name) {
    double total = 0;
    boolean any = false;
    for (Meter m : meterRegistry.find(name).meters()) {
      if (m instanceof Gauge g) {
        double v = g.value();
        if (Double.isFinite(v)) {
          total += v;
          any = true;
        }
      }
    }
    return any ? total : 0;
  }

  private static boolean closeTo(double a, double b) {
    return Math.abs(a - b) < 1e-6;
  }
}
