package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AdminHealthServiceTest {

  @Test
  void countsRequestsByStatusFamily() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer.builder("http.server.requests")
        .tag("status", "200")
        .register(registry)
        .record(10, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("status", "201")
        .register(registry)
        .record(20, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("status", "404")
        .register(registry)
        .record(5, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("status", "500")
        .register(registry)
        .record(50, TimeUnit.MILLISECONDS);
    AdminHealthService service = new AdminHealthService(registry);

    AdminHealthMetrics.HttpStatusCounts counts = service.statusCounts();
    assertThat(counts.count2xx()).isEqualTo(2);
    assertThat(counts.count4xx()).isEqualTo(1);
    assertThat(counts.count5xx()).isEqualTo(1);
  }

  @Test
  void aggregatesNamedCounters() {
    MeterRegistry registry = new SimpleMeterRegistry();
    registry.counter("rate_limit.exceeded", "scope", "anonymous").increment(7);
    registry.counter("rate_limit.exceeded", "scope", "user").increment(3);
    registry.counter("safe_browsing.check", "result", "malicious").increment(4);
    registry.counter("safe_browsing.check", "result", "safe").increment(11);
    registry.counter("auth.failure").increment(9);
    AdminHealthService service = new AdminHealthService(registry);

    AdminHealthMetrics m = service.metrics();
    assertThat(m.rateLimitExceeded()).isEqualTo(10);
    assertThat(m.safeBrowsingMalicious()).isEqualTo(4);
    assertThat(m.authFailures()).isEqualTo(9);
  }

  @Test
  void cacheRatioFromGetsCounter() {
    MeterRegistry registry = new SimpleMeterRegistry();
    registry.counter("cache.gets", "result", "hit").increment(80);
    registry.counter("cache.gets", "result", "miss").increment(20);
    AdminHealthService service = new AdminHealthService(registry);

    AdminHealthMetrics.Cache cache = service.cache();
    assertThat(cache.hits()).isEqualTo(80);
    assertThat(cache.misses()).isEqualTo(20);
    assertThat(cache.hitRatio()).isEqualTo(0.8);
  }

  @Test
  void emptyRegistryReturnsZeros() {
    MeterRegistry registry = new SimpleMeterRegistry();
    AdminHealthService service = new AdminHealthService(registry);

    AdminHealthMetrics m = service.metrics();
    assertThat(m.rateLimitExceeded()).isZero();
    assertThat(m.safeBrowsingMalicious()).isZero();
    assertThat(m.authFailures()).isZero();
    assertThat(m.cache().hitRatio()).isZero();
    assertThat(m.httpStatusCounts().count2xx()).isZero();
    assertThat(m.redirect().total()).isZero();
  }

  @Test
  void redirectMetricsAggregateOutcomes() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer.builder("redirect.latency")
        .tag("outcome", "redirect")
        .register(registry)
        .record(2, TimeUnit.MILLISECONDS);
    Timer.builder("redirect.latency")
        .tag("outcome", "redirect")
        .register(registry)
        .record(3, TimeUnit.MILLISECONDS);
    Timer.builder("redirect.latency")
        .tag("outcome", "preview")
        .register(registry)
        .record(7, TimeUnit.MILLISECONDS);
    Timer.builder("redirect.latency")
        .tag("outcome", "not_found")
        .register(registry)
        .record(1, TimeUnit.MILLISECONDS);
    Timer.builder("redirect.latency")
        .tag("outcome", "expired")
        .register(registry)
        .record(1, TimeUnit.MILLISECONDS);
    AdminHealthService service = new AdminHealthService(registry);

    AdminHealthMetrics.RedirectPerf perf = service.redirect();
    assertThat(perf.total()).isEqualTo(5);
    assertThat(perf.redirects()).isEqualTo(2);
    assertThat(perf.previews()).isEqualTo(1);
    assertThat(perf.notFound()).isEqualTo(1);
    assertThat(perf.expired()).isEqualTo(1);
  }
}
