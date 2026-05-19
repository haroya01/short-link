package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminRequestMetricsServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void routesAggregatesPercentilesAndErrorRatePerRoute() {
    RequestMetricRepository repo = mock(RequestMetricRepository.class);
    when(repo.findWindow(any(), any()))
        .thenReturn(
            List.of(
                row("/r/{shortCode}", "GET", 302, "redirect", 5),
                row("/r/{shortCode}", "GET", 302, "redirect", 15),
                row("/r/{shortCode}", "GET", 302, "redirect", 25),
                row("/r/{shortCode}", "GET", 500, "error", 250),
                row("/api/links", "POST", 201, "ok", 40)));
    AdminRequestMetricsService service = new AdminRequestMetricsService(repo, fixedClock);

    List<AdminRequestMetricsService.RouteAggregate> agg =
        service.routes(AdminRequestMetricsService.Window.H1);

    assertThat(agg).hasSize(2);
    // Sorted descending by count — redirect route comes first
    AdminRequestMetricsService.RouteAggregate top = agg.get(0);
    assertThat(top.method()).isEqualTo("GET");
    assertThat(top.route()).isEqualTo("/r/{shortCode}");
    assertThat(top.count()).isEqualTo(4L);
    assertThat(top.errorRate()).isEqualTo(0.25); // 1 of 4 is a 500
    assertThat(top.statusDistribution()).containsEntry("302", 3L).containsEntry("500", 1L);
    assertThat(top.outcomeDistribution()).containsEntry("redirect", 3L).containsEntry("error", 1L);
  }

  @Test
  void outcomesGroupsByOutcomeForShortCode() {
    RequestMetricRepository repo = mock(RequestMetricRepository.class);
    when(repo.findShortCodeWindow(any(), any(), any()))
        .thenReturn(
            List.of(
                row("/r/{shortCode}", "GET", 302, "redirect", 5),
                row("/r/{shortCode}", "GET", 302, "redirect", 7),
                row("/r/{shortCode}", "GET", 410, "expired", 9),
                row("/r/{shortCode}", "GET", 451, "blocked", 12)));
    AdminRequestMetricsService service = new AdminRequestMetricsService(repo, fixedClock);

    AdminRequestMetricsService.OutcomeDistribution dist =
        service.outcomes("abc1234", AdminRequestMetricsService.Window.H24);

    assertThat(dist.shortCode()).isEqualTo("abc1234");
    assertThat(dist.total()).isEqualTo(4);
    assertThat(dist.outcomes())
        .containsEntry("redirect", 2L)
        .containsEntry("expired", 1L)
        .containsEntry("blocked", 1L);
  }

  @Test
  void outcomesRejectsBlankShortCode() {
    AdminRequestMetricsService service =
        new AdminRequestMetricsService(mock(RequestMetricRepository.class), fixedClock);
    assertThatThrownBy(() -> service.outcomes("  ", AdminRequestMetricsService.Window.H1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.outcomes(null, AdminRequestMetricsService.Window.H1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rawFiltersComposeAndRespectLimit() {
    RequestMetricRepository repo = mock(RequestMetricRepository.class);
    when(repo.findWindow(any(), any()))
        .thenReturn(
            List.of(
                row("/api/links", "GET", 200, "ok", 10),
                row("/r/{shortCode}", "GET", 302, "redirect", 5),
                row("/r/{shortCode}", "GET", 404, "not_found", 8),
                row("/api/links", "POST", 201, "ok", 20),
                row("/api/links", "POST", 500, "error", 30)));
    AdminRequestMetricsService service = new AdminRequestMetricsService(repo, fixedClock);

    List<AdminRequestMetricsService.RawRow> errorsOnly =
        service.raw(
            new AdminRequestMetricsService.RawQuery(null, null, null, "error", null, null, null));
    assertThat(errorsOnly).hasSize(1);
    assertThat(errorsOnly.get(0).status()).isEqualTo(500);

    List<AdminRequestMetricsService.RawRow> limited =
        service.raw(new AdminRequestMetricsService.RawQuery(null, null, null, null, null, null, 2));
    assertThat(limited).hasSize(2);
  }

  private static RequestMetricEntity row(
      String route, String method, int status, String outcome, long latency) {
    return new RequestMetricEntity(
        new RequestMetric(NOW, route, method, status, outcome, latency, null, null, null));
  }
}
