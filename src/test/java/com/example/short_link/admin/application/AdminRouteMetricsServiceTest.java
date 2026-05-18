package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AdminRouteMetricsServiceTest {

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
    AdminRouteMetricsService service = new AdminRouteMetricsService(registry);

    List<AdminRouteMetric> metrics = service.routeMetrics();

    assertThat(metrics).hasSize(2);
    AdminRouteMetric post =
        metrics.stream().filter(m -> m.method().equals("POST")).findFirst().orElseThrow();
    assertThat(post.uri()).isEqualTo("/api/v1/links");
    assertThat(post.count()).isEqualTo(2);
    assertThat(post.error5xxCount()).isEqualTo(1);
    assertThat(post.errorRate()).isEqualTo(0.5);
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
    AdminRouteMetricsService service = new AdminRouteMetricsService(registry);

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
    AdminRouteMetricsService service = new AdminRouteMetricsService(registry);

    assertThat(service.routeMetrics()).isEmpty();
  }

  @Test
  void emptyRegistryReturnsEmptyList() {
    AdminRouteMetricsService service = new AdminRouteMetricsService(new SimpleMeterRegistry());
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
    AdminRouteMetricsService service = new AdminRouteMetricsService(registry);

    AdminRouteMetric m = service.routeMetrics().get(0);
    assertThat(m.errorRate()).isZero();
    assertThat(m.error5xxCount()).isZero();
  }
}
