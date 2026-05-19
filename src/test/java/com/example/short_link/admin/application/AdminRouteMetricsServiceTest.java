package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AdminRouteMetricsServiceTest {

  private static AdminRouteMetricsService newService(MeterRegistry registry) {
    return new AdminRouteMetricsService(registry, new RouteMetricsRingBuffer(registry));
  }

  @Test
  void groupsByUriAndMethodAcrossStatusTags() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer.builder("http.server.requests")
        .tag("uri", "/api/v1/links")
        .tag("method", "POST")
        .tag("status", "200")
        .tag("outcome", "SUCCESS")
        .register(registry)
        .record(10, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("uri", "/api/v1/links")
        .tag("method", "POST")
        .tag("status", "500")
        .tag("outcome", "SERVER_ERROR")
        .register(registry)
        .record(80, TimeUnit.MILLISECONDS);
    Timer.builder("http.server.requests")
        .tag("uri", "/api/v1/links")
        .tag("method", "GET")
        .tag("status", "200")
        .tag("outcome", "SUCCESS")
        .register(registry)
        .record(5, TimeUnit.MILLISECONDS);
    AdminRouteMetricsService service = newService(registry);

    List<AdminRouteMetric> metrics = service.routeMetrics();

    assertThat(metrics).hasSize(2);
    AdminRouteMetric post =
        metrics.stream().filter(m -> m.method().equals("POST")).findFirst().orElseThrow();
    assertThat(post.uri()).isEqualTo("/api/v1/links");
    assertThat(post.count()).isEqualTo(2);
    assertThat(post.error5xxCount()).isEqualTo(1);
    assertThat(post.errorRate()).isEqualTo(0.5);
    assertThat(post.statusDistribution()).containsEntry("200", 1L).containsEntry("500", 1L);
  }

  @Test
  void sortsByCountDescending() {
    MeterRegistry registry = new SimpleMeterRegistry();
    for (int i = 0; i < 3; i++) {
      Timer.builder("http.server.requests")
          .tag("uri", "/api/v1/quiet")
          .tag("method", "GET")
          .tag("status", "200")
          .register(registry)
          .record(1, TimeUnit.MILLISECONDS);
    }
    for (int i = 0; i < 10; i++) {
      Timer.builder("http.server.requests")
          .tag("uri", "/api/v1/busy")
          .tag("method", "GET")
          .tag("status", "200")
          .register(registry)
          .record(1, TimeUnit.MILLISECONDS);
    }
    AdminRouteMetricsService service = newService(registry);

    List<AdminRouteMetric> metrics = service.routeMetrics();

    assertThat(metrics)
        .extracting(AdminRouteMetric::uri)
        .containsExactly("/api/v1/busy", "/api/v1/quiet");
  }

  @Test
  void skipsTimersMissingUriOrMethodTag() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer.builder("http.server.requests")
        .tag("status", "200")
        .register(registry)
        .record(10, TimeUnit.MILLISECONDS);
    AdminRouteMetricsService service = newService(registry);

    assertThat(service.routeMetrics()).isEmpty();
  }

  @Test
  void emptyRegistryReturnsEmptyList() {
    AdminRouteMetricsService service = newService(new SimpleMeterRegistry());
    assertThat(service.routeMetrics()).isEmpty();
  }

  @Test
  void errorRateIsZeroWhenAllSamplesAreSuccess() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer.builder("http.server.requests")
        .tag("uri", "/api/v1/healthy")
        .tag("method", "GET")
        .tag("status", "200")
        .register(registry)
        .record(1, TimeUnit.MILLISECONDS);
    AdminRouteMetricsService service = newService(registry);

    AdminRouteMetric m = service.routeMetrics().get(0);
    assertThat(m.errorRate()).isZero();
    assertThat(m.error5xxCount()).isZero();
    assertThat(m.statusDistribution()).containsEntry("200", 1L);
  }

  @Test
  void windowReturnsRingBufferDeltas() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Timer timer =
        Timer.builder("http.server.requests")
            .tag("uri", "/api/v1/links")
            .tag("method", "GET")
            .tag("status", "200")
            .register(registry);
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    RouteMetricsRingBuffer ring = new RouteMetricsRingBuffer(registry, clock);
    AdminRouteMetricsService service = new AdminRouteMetricsService(registry, ring);

    // first sample establishes baseline only
    timer.record(5, TimeUnit.MILLISECONDS);
    ring.sample();
    // advance a minute, record more events, second sample captures the delta
    clock.advanceMinutes(1);
    for (int i = 0; i < 7; i++) timer.record(5, TimeUnit.MILLISECONDS);
    ring.sample();

    List<AdminRouteMetric> hour = service.routeMetricsWindow(AdminRouteMetricsService.Window.H1);
    assertThat(hour).hasSize(1);
    assertThat(hour.get(0).count()).isEqualTo(7);
    assertThat(hour.get(0).statusDistribution()).containsEntry("200", 7L);
  }

  @Test
  void windowParseAcceptsShortAliases() {
    assertThat(AdminRouteMetricsService.Window.parse("1h"))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
    assertThat(AdminRouteMetricsService.Window.parse("24h"))
        .isEqualTo(AdminRouteMetricsService.Window.H24);
    assertThat(AdminRouteMetricsService.Window.parse("7d"))
        .isEqualTo(AdminRouteMetricsService.Window.D7);
    assertThat(AdminRouteMetricsService.Window.parse(null))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
    assertThat(AdminRouteMetricsService.Window.parse("nonsense"))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
  }

  /** Time-only fake — enough for the ring-buffer windowing logic. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advanceMinutes(int minutes) {
      this.now = this.now.plusSeconds(minutes * 60L);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
