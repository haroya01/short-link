package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.AdminMetricsRepository.LinkMetricRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminLinkMetricsServiceTest {

  @Test
  void mergesRingSamplesWithDbMetadata() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    recorder.record("abc123", 10, "redirect");
    recorder.record("abc123", 50, "redirect");
    recorder.record("abc123", 999, "not_found");

    AdminMetricsRepository repo = mock(AdminMetricsRepository.class);
    LinkMetricRow row = stubRow("abc123", "https://a.com", 7L, "owner@x.com", 42L, Instant.EPOCH);
    when(repo.linkMetricRowsByShortCodes(anyCollection())).thenReturn(List.of(row));

    AdminLinkMetricsService service = new AdminLinkMetricsService(recorder, repo);

    List<AdminLinkMetric> out =
        service.linkMetrics(AdminLinkMetricsService.Window.H24, AdminLinkMetricsService.Sort.COUNT);

    assertThat(out).hasSize(1);
    AdminLinkMetric m = out.get(0);
    assertThat(m.shortCode()).isEqualTo("abc123");
    assertThat(m.originalUrl()).isEqualTo("https://a.com");
    assertThat(m.userId()).isEqualTo(7L);
    assertThat(m.ownerEmail()).isEqualTo("owner@x.com");
    assertThat(m.totalRedirects()).isEqualTo(42L);
    assertThat(m.windowedRedirects()).isEqualTo(3L);
    assertThat(m.outcomeCounts()).containsEntry("redirect", 2L).containsEntry("not_found", 1L);
    assertThat(m.errorRate()).isGreaterThan(0.0);
  }

  @Test
  void sortsByLatencyDesc() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    for (int i = 0; i < 5; i++) recorder.record("fast", 5, "redirect");
    for (int i = 0; i < 5; i++) recorder.record("slow", 500, "redirect");

    AdminMetricsRepository repo = mock(AdminMetricsRepository.class);
    when(repo.linkMetricRowsByShortCodes(anyCollection()))
        .thenReturn(
            List.of(
                stubRow("fast", "https://f.com", 1L, "a@x", 5L, Instant.EPOCH),
                stubRow("slow", "https://s.com", 1L, "a@x", 5L, Instant.EPOCH)));

    AdminLinkMetricsService service = new AdminLinkMetricsService(recorder, repo);

    List<AdminLinkMetric> out =
        service.linkMetrics(
            AdminLinkMetricsService.Window.H24, AdminLinkMetricsService.Sort.LATENCY);

    assertThat(out).extracting(AdminLinkMetric::shortCode).containsExactly("slow", "fast");
  }

  @Test
  void sortsByErrorRateDesc() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    for (int i = 0; i < 10; i++) recorder.record("clean", 10, "redirect");
    recorder.record("noisy", 10, "redirect");
    recorder.record("noisy", 10, "not_found");
    recorder.record("noisy", 10, "blocked");

    AdminMetricsRepository repo = mock(AdminMetricsRepository.class);
    when(repo.linkMetricRowsByShortCodes(anyCollection()))
        .thenReturn(
            List.of(
                stubRow("clean", "https://c.com", 1L, "a@x", 10L, Instant.EPOCH),
                stubRow("noisy", "https://n.com", 1L, "a@x", 3L, Instant.EPOCH)));

    AdminLinkMetricsService service = new AdminLinkMetricsService(recorder, repo);

    List<AdminLinkMetric> out =
        service.linkMetrics(AdminLinkMetricsService.Window.H24, AdminLinkMetricsService.Sort.ERROR);

    assertThat(out).extracting(AdminLinkMetric::shortCode).containsExactly("noisy", "clean");
  }

  @Test
  void emptyRecorderReturnsEmpty() {
    AdminMetricsRepository repo = mock(AdminMetricsRepository.class);
    AdminLinkMetricsService service = new AdminLinkMetricsService(new LinkMetricsRecorder(), repo);
    assertThat(
            service.linkMetrics(
                AdminLinkMetricsService.Window.H1, AdminLinkMetricsService.Sort.COUNT))
        .isEmpty();
  }

  @Test
  void missingDbRowFallsBackToRingTotals() {
    LinkMetricsRecorder recorder = new LinkMetricsRecorder();
    recorder.record("ghost", 10, "redirect");
    AdminMetricsRepository repo = mock(AdminMetricsRepository.class);
    when(repo.linkMetricRowsByShortCodes(anyCollection())).thenReturn(List.of());

    AdminLinkMetricsService service = new AdminLinkMetricsService(recorder, repo);
    List<AdminLinkMetric> out =
        service.linkMetrics(AdminLinkMetricsService.Window.H24, AdminLinkMetricsService.Sort.COUNT);

    assertThat(out).hasSize(1);
    AdminLinkMetric m = out.get(0);
    assertThat(m.originalUrl()).isNull();
    assertThat(m.ownerEmail()).isNull();
    assertThat(m.totalRedirects()).isEqualTo(1L);
    assertThat(m.windowedRedirects()).isEqualTo(1L);
  }

  @Test
  void windowParseAcceptsAliases() {
    assertThat(AdminLinkMetricsService.Window.parse("1h"))
        .isEqualTo(AdminLinkMetricsService.Window.H1);
    assertThat(AdminLinkMetricsService.Window.parse("24h"))
        .isEqualTo(AdminLinkMetricsService.Window.H24);
    assertThat(AdminLinkMetricsService.Window.parse("7d"))
        .isEqualTo(AdminLinkMetricsService.Window.D7);
    assertThat(AdminLinkMetricsService.Window.parse("all"))
        .isEqualTo(AdminLinkMetricsService.Window.ALL);
    assertThat(AdminLinkMetricsService.Window.parse(null))
        .isEqualTo(AdminLinkMetricsService.Window.H24);
    assertThat(AdminLinkMetricsService.Window.parse("nonsense"))
        .isEqualTo(AdminLinkMetricsService.Window.H24);
  }

  @Test
  void sortParseAcceptsAliases() {
    assertThat(AdminLinkMetricsService.Sort.parse("count"))
        .isEqualTo(AdminLinkMetricsService.Sort.COUNT);
    assertThat(AdminLinkMetricsService.Sort.parse("latency"))
        .isEqualTo(AdminLinkMetricsService.Sort.LATENCY);
    assertThat(AdminLinkMetricsService.Sort.parse("p95"))
        .isEqualTo(AdminLinkMetricsService.Sort.LATENCY);
    assertThat(AdminLinkMetricsService.Sort.parse("error"))
        .isEqualTo(AdminLinkMetricsService.Sort.ERROR);
    assertThat(AdminLinkMetricsService.Sort.parse(null))
        .isEqualTo(AdminLinkMetricsService.Sort.COUNT);
  }

  private static LinkMetricRow stubRow(
      String shortCode,
      String originalUrl,
      Long userId,
      String ownerEmail,
      Long totalRedirects,
      Instant lastRedirectAt) {
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
        return ownerEmail;
      }

      @Override
      public Long getTotalRedirects() {
        return totalRedirects;
      }

      @Override
      public Instant getLastRedirectAt() {
        return lastRedirectAt;
      }
    };
  }
}
