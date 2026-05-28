package com.example.short_link.admin.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.dto.AdminLinkMetric;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.common.observability.RequestMetric;
import com.example.short_link.common.observability.RequestMetricEntity;
import com.example.short_link.common.observability.RequestMetricJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminLinkMetricsQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-19T10:00:00Z");
  private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void groupsShortCodesAndJoinsDbMetadata() {
    RequestMetricJpaRepository requestRepo = mock(RequestMetricJpaRepository.class);
    when(requestRepo.findWindow(any(), any()))
        .thenReturn(
            List.of(
                row("abc1234", 302, "redirect", 10),
                row("abc1234", 302, "redirect", 30),
                row("abc1234", 404, "not_found", 100),
                row("xyz9999", 302, "redirect", 50)));
    AdminMetricsRepository metricsRepo = mock(AdminMetricsRepository.class);
    when(metricsRepo.linkMetricRowsByShortCodes(anyCollection()))
        .thenReturn(
            List.of(linkMetricRow("abc1234", "https://example.com/a", 42L, 99L, "x@y.com")));

    AdminLinkMetricsQueryService service =
        new AdminLinkMetricsQueryService(requestRepo, metricsRepo, fixedClock);
    List<AdminLinkMetric> metrics =
        service.linkMetrics(
            AdminLinkMetricsQueryService.Window.H24, AdminLinkMetricsQueryService.Sort.COUNT);

    assertThat(metrics).hasSize(2);
    AdminLinkMetric abc =
        metrics.stream().filter(m -> m.shortCode().equals("abc1234")).findFirst().orElseThrow();
    assertThat(abc.windowedRedirects()).isEqualTo(3);
    assertThat(abc.outcomeCounts()).containsEntry("redirect", 2L).containsEntry("not_found", 1L);
    assertThat(abc.totalRedirects()).isEqualTo(99L);
    assertThat(abc.originalUrl()).isEqualTo("https://example.com/a");
    assertThat(abc.ownerEmail()).isEqualTo("x@y.com");

    AdminLinkMetric xyz =
        metrics.stream().filter(m -> m.shortCode().equals("xyz9999")).findFirst().orElseThrow();
    assertThat(xyz.totalRedirects()).isEqualTo(1);
    assertThat(xyz.originalUrl()).isNull();
  }

  @Test
  void rowsWithoutShortCodeAreIgnored() {
    RequestMetricJpaRepository requestRepo = mock(RequestMetricJpaRepository.class);
    when(requestRepo.findWindow(any(), any()))
        .thenReturn(
            List.of(
                row(null, 200, "ok", 12), row("", 200, "ok", 14), row("real", 302, "redirect", 8)));
    AdminMetricsRepository metricsRepo = mock(AdminMetricsRepository.class);
    when(metricsRepo.linkMetricRowsByShortCodes(anyCollection())).thenReturn(List.of());

    AdminLinkMetricsQueryService service =
        new AdminLinkMetricsQueryService(requestRepo, metricsRepo, fixedClock);

    List<AdminLinkMetric> metrics =
        service.linkMetrics(
            AdminLinkMetricsQueryService.Window.H1, AdminLinkMetricsQueryService.Sort.COUNT);
    assertThat(metrics).hasSize(1);
    assertThat(metrics.get(0).shortCode()).isEqualTo("real");
  }

  @Test
  void emptySliceReturnsEmpty() {
    RequestMetricJpaRepository requestRepo = mock(RequestMetricJpaRepository.class);
    when(requestRepo.findWindow(any(), any())).thenReturn(List.of());
    AdminMetricsRepository metricsRepo = mock(AdminMetricsRepository.class);
    AdminLinkMetricsQueryService service =
        new AdminLinkMetricsQueryService(requestRepo, metricsRepo, fixedClock);

    assertThat(
            service.linkMetrics(
                AdminLinkMetricsQueryService.Window.D7, AdminLinkMetricsQueryService.Sort.COUNT))
        .isEmpty();
  }

  private static RequestMetricEntity row(String shortCode, int status, String outcome, long ms) {
    return new RequestMetricEntity(
        new RequestMetric(
            NOW, "/r/{shortCode}", "GET", status, outcome, ms, shortCode, null, null));
  }

  private static LinkMetricRow linkMetricRow(
      String shortCode, String originalUrl, Long userId, Long totalRedirects, String email) {
    return new LinkMetricRow() {
      @Override
      public String getShortCode() {
        return shortCode;
      }

      @Override
      public String getOriginalUrl() {
        return originalUrl;
      }

      @Override
      public Long getUserId() {
        return userId;
      }

      @Override
      public String getOwnerEmail() {
        return email;
      }

      @Override
      public Long getTotalRedirects() {
        return totalRedirects;
      }

      @Override
      public Instant getLastRedirectAt() {
        return NOW;
      }
    };
  }
}
