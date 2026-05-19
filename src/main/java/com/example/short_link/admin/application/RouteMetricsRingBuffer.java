package com.example.short_link.admin.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-minute snapshot ring of {@code http.server.requests} keyed by {@code (method, uri, status)}.
 * Micrometer's built-in timer is monotonically cumulative — a {@code count} reading at T2 minus the
 * reading at T1 gives you the volume in that interval. We sample the registry every minute and
 * stash the delta, so the admin UI can ask for "last 1h" / "last 24h" / "last 7d" windows without
 * an external TSDB.
 *
 * <p>Buffer size = {@link #CAPACITY_MINUTES} (10080 = 7 days). Each (method, uri, status) tuple
 * carries its own ring; total memory is bounded by {@code routes × statuses × 7d × ~40B} which
 * stays under ~20MB even with a few hundred (route, status) tuples — the actual app exposes well
 * under that.
 *
 * <p>Percentile fidelity: Micrometer histogram percentiles are also cumulative, so a delta would be
 * meaningless. Instead we store the {@code p95}/{@code p99} snapshot value at sample time and
 * report the {@code MAX} across the requested window — that surfaces the worst minute, which is
 * what an operator wants to see during a slow-API incident.
 */
@Slf4j
@Component
public class RouteMetricsRingBuffer {

  static final int CAPACITY_MINUTES = 7 * 24 * 60; // 10080
  private static final String HTTP_TIMER = "http.server.requests";

  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final ReentrantLock writeLock = new ReentrantLock();

  /** Cumulative count read on the previous tick — used to derive the per-minute delta. */
  private final Map<TupleKey, Long> lastCount = new HashMap<>();

  /** Per-tuple ring of minute snapshots, ordered oldest-first; trimmed to CAPACITY_MINUTES. */
  private final Map<TupleKey, ArrayList<MinuteBucket>> rings = new HashMap<>();

  public RouteMetricsRingBuffer(MeterRegistry meterRegistry) {
    this(meterRegistry, Clock.systemUTC());
  }

  RouteMetricsRingBuffer(MeterRegistry meterRegistry, Clock clock) {
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  /**
   * Runs once a minute. Each (method, uri, status) timer contributes a {@link MinuteBucket}
   * carrying the count delta, error tally, and the p95/p99 it observed during this minute. The very
   * first tick after boot seeds {@link #lastCount} and produces no bucket — otherwise the delta
   * would equal the entire pre-startup count and skew the first window.
   */
  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  public void sample() {
    Instant now = clock.instant();
    writeLock.lock();
    try {
      for (Timer t : meterRegistry.find(HTTP_TIMER).timers()) {
        String uri = t.getId().getTag("uri");
        String method = t.getId().getTag("method");
        String status = t.getId().getTag("status");
        if (uri == null || method == null || status == null) continue;
        TupleKey key = new TupleKey(method, uri, status);
        long cumulative = t.count();
        Long previous = lastCount.put(key, cumulative);
        if (previous == null) continue; // first observation — establish baseline only
        long delta = cumulative - previous;
        if (delta < 0) delta = 0; // registry reset / counter rolled
        if (delta == 0) continue;
        double p95 = percentile(t, 0.95);
        double p99 = percentile(t, 0.99);
        MinuteBucket bucket = new MinuteBucket(now, delta, p95, p99);
        ArrayList<MinuteBucket> ring = rings.computeIfAbsent(key, k -> new ArrayList<>());
        ring.add(bucket);
        if (ring.size() > CAPACITY_MINUTES) {
          ring.remove(0);
        }
      }
    } catch (Exception e) {
      log.warn("route-metrics ring sample failed", e);
    } finally {
      writeLock.unlock();
    }
  }

  /** Returns the buckets within {@code [now - windowMinutes, now]}, grouped by (method, uri). */
  Map<RouteKey, List<TupleSlice>> sliceWindow(int windowMinutes) {
    Instant cutoff = clock.instant().minusSeconds(windowMinutes * 60L);
    Map<RouteKey, List<TupleSlice>> out = new HashMap<>();
    writeLock.lock();
    try {
      for (Map.Entry<TupleKey, ArrayList<MinuteBucket>> entry : rings.entrySet()) {
        TupleKey tuple = entry.getKey();
        ArrayList<MinuteBucket> ring = entry.getValue();
        List<MinuteBucket> inWindow = new ArrayList<>();
        for (int i = ring.size() - 1; i >= 0; i--) {
          MinuteBucket b = ring.get(i);
          if (b.timestamp().isBefore(cutoff)) break;
          inWindow.add(0, b);
        }
        if (inWindow.isEmpty()) continue;
        RouteKey routeKey = new RouteKey(tuple.method(), tuple.uri());
        out.computeIfAbsent(routeKey, k -> new ArrayList<>())
            .add(new TupleSlice(tuple.status(), inWindow));
      }
    } finally {
      writeLock.unlock();
    }
    return out;
  }

  private static double percentile(Timer t, double p) {
    for (var v : t.takeSnapshot().percentileValues()) {
      if (Math.abs(v.percentile() - p) < 1e-6) {
        return v.value(TimeUnit.MILLISECONDS);
      }
    }
    return 0.0;
  }

  record TupleKey(String method, String uri, String status) {}

  record RouteKey(String method, String uri) {}

  record MinuteBucket(Instant timestamp, long count, double p95Millis, double p99Millis) {}

  record TupleSlice(String status, List<MinuteBucket> buckets) {}
}
