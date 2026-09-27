package com.example.short_link.admin.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.dto.AdminRouteMetric;
import com.example.short_link.common.observability.RequestMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminRouteMetricsServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void aggregatesPerMethodAndUriWithPercentilesAndErrorRate() {
    RequestMetricJpaRepository repo = mock(RequestMetricJpaRepository.class);
    when(repo.findWindow(any(), any()))
        .thenReturn(
            List.of(
                row("/r/{shortCode}", "GET", 302, "redirect", 5),
                row("/r/{shortCode}", "GET", 302, "redirect", 15),
                row("/r/{shortCode}", "GET", 302, "redirect", 25),
                row("/r/{shortCode}", "GET", 500, "error", 250),
                row("/api/links", "POST", 201, "ok", 40)));
    AdminRouteMetricsService service = new AdminRouteMetricsService(repo, fixedClock);

    List<AdminRouteMetric> agg = service.routeMetricsWindow(AdminRouteMetricsService.Window.H1);

    assertThat(agg).hasSize(2);
    AdminRouteMetric top = agg.get(0);
    assertThat(top.method()).isEqualTo("GET");
    assertThat(top.uri()).isEqualTo("/r/{shortCode}");
    assertThat(top.count()).isEqualTo(4L);
    assertThat(top.p50Millis()).isEqualTo(20.0);
    assertThat(top.p95Millis()).isCloseTo(216.25, within(0.0001));
    assertThat(top.p99Millis()).isCloseTo(243.25, within(0.0001));
    assertThat(top.error5xxCount()).isEqualTo(1L);
    assertThat(top.errorRate()).isCloseTo(0.25, within(0.0001));
    assertThat(top.statusDistribution()).containsEntry("302", 3L).containsEntry("500", 1L);
    assertThat(top.statusDistribution().keySet()).containsExactly("302", "500");
  }

  @Test
  void lifetimeSummaryKeepsSevenDayWindowAndDoesNotRequireOutcomes() {
    RequestMetricJpaRepository repo = mock(RequestMetricJpaRepository.class);
    when(repo.findWindow(any(), any()))
        .thenReturn(
            List.of(row("/same", "GET", 500, null, 10), row("/same", "POST", 200, null, 20)));

    List<AdminRouteMetric> metrics = new AdminRouteMetricsService(repo, fixedClock).routeMetrics();

    verify(repo).findWindow(NOW.minus(Duration.ofDays(7)), NOW);
    assertThat(metrics)
        .extracting(AdminRouteMetric::method)
        .containsExactlyInAnyOrder("GET", "POST");
    assertThat(metrics).allMatch(metric -> metric.count() == 1);
  }

  @Test
  void emptyWindowReturnsEmpty() {
    RequestMetricJpaRepository repo = mock(RequestMetricJpaRepository.class);
    when(repo.findWindow(any(), any())).thenReturn(List.of());
    AdminRouteMetricsService service = new AdminRouteMetricsService(repo, fixedClock);

    assertThat(service.routeMetrics()).isEmpty();
    assertThat(service.routeMetricsWindow(AdminRouteMetricsService.Window.D7)).isEmpty();
  }

  @Test
  void windowParsingNormalizesAliases() {
    assertThat(AdminRouteMetricsService.Window.parse("1h"))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
    assertThat(AdminRouteMetricsService.Window.parse("24h"))
        .isEqualTo(AdminRouteMetricsService.Window.H24);
    assertThat(AdminRouteMetricsService.Window.parse("week"))
        .isEqualTo(AdminRouteMetricsService.Window.D7);
    assertThat(AdminRouteMetricsService.Window.parse(null))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
    assertThat(AdminRouteMetricsService.Window.parse("unknown"))
        .isEqualTo(AdminRouteMetricsService.Window.H1);
  }

  private static RequestMetricEntity row(
      String route, String method, int status, String outcome, long latency) {
    return new RequestMetricEntity(
        new RequestMetric(NOW, route, method, status, outcome, latency, null, null, null));
  }
}
