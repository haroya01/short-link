package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.AdminActiveUsers;
import com.example.short_link.admin.application.AdminAnalyticsService;
import com.example.short_link.admin.application.AdminCohort;
import com.example.short_link.admin.application.AdminHealthMetrics;
import com.example.short_link.admin.application.AdminHealthService;
import com.example.short_link.admin.application.AdminLifecycle;
import com.example.short_link.admin.application.AdminLinkMetric;
import com.example.short_link.admin.application.AdminOverview;
import com.example.short_link.admin.application.AdminOverviewService;
import com.example.short_link.admin.application.AdminRouteMetric;
import com.example.short_link.admin.application.AdminRouteMetricsService;
import com.example.short_link.admin.application.RecentError;
import com.example.short_link.admin.application.RecentErrorsService;
import com.example.short_link.admin.application.read.AdminLinkMetricsQueryService;
import com.example.short_link.common.observability.AdminFunnelService;
import com.example.short_link.common.observability.AdminRequestMetricsService;
import com.example.short_link.common.observability.AdminSystemMetricsService;
import com.example.short_link.link.application.WebhookReDetectResult;
import com.example.short_link.link.application.write.ReDetectWebhookFormatsUseCase;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminOverviewService service;
  private final AdminHealthService healthService;
  private final RecentErrorsService recentErrorsService;
  private final AdminAnalyticsService analyticsService;
  private final AdminRouteMetricsService routeMetricsService;
  private final AdminLinkMetricsQueryService linkMetricsService;
  private final ReDetectWebhookFormatsUseCase reDetectWebhooks;
  private final AdminRequestMetricsService requestMetricsService;
  private final AdminSystemMetricsService systemMetricsService;
  private final AdminFunnelService funnelService;

  @GetMapping("/overview")
  public AdminOverview overview() {
    return service.overview();
  }

  @GetMapping("/top-users-by-links")
  public AdminOverviewService.TopUsersPage topUsersByLinks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    return service.topUsersByLinks(page, size);
  }

  @GetMapping("/top-users-by-clicks")
  public AdminOverviewService.TopUsersPage topUsersByClicks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    return service.topUsersByClicks(page, size);
  }

  @GetMapping("/top-links-by-clicks")
  public AdminOverviewService.TopLinksPage topLinksByClicks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
    return service.topLinksByClicks(page, size);
  }

  @GetMapping("/health-metrics")
  public AdminHealthMetrics healthMetrics() {
    return healthService.metrics();
  }

  @GetMapping("/route-metrics")
  public List<AdminRouteMetric> routeMetrics(
      @RequestParam(name = "window", required = false) String window) {
    if (window == null || window.isBlank() || "all".equalsIgnoreCase(window)) {
      return routeMetricsService.routeMetrics();
    }
    return routeMetricsService.routeMetricsWindow(AdminRouteMetricsService.Window.parse(window));
  }

  @GetMapping("/link-metrics")
  public List<AdminLinkMetric> linkMetrics(
      @RequestParam(name = "window", required = false) String window,
      @RequestParam(name = "sort", required = false) String sort) {
    return linkMetricsService.linkMetrics(
        AdminLinkMetricsQueryService.Window.parse(window),
        AdminLinkMetricsQueryService.Sort.parse(sort));
  }

  @GetMapping("/recent-errors")
  public List<RecentError> recentErrors(@RequestParam(required = false) Integer limit) {
    return recentErrorsService.recent(limit);
  }

  @GetMapping("/cohort")
  public AdminCohort cohort(@RequestParam(required = false, defaultValue = "8") int weeks) {
    return analyticsService.cohort(weeks);
  }

  @GetMapping("/lifecycle")
  public AdminLifecycle lifecycle(@RequestParam(required = false, defaultValue = "30") int days) {
    return analyticsService.lifecycle(days);
  }

  @GetMapping("/active-users")
  public AdminActiveUsers activeUsers(
      @RequestParam(required = false, defaultValue = "day") String period) {
    return analyticsService.activeUsers(period);
  }

  /**
   * Re-detects {@code WebhookFormat} for every persisted hook and reactivates rows that were
   * auto-disabled by a payload-shape mismatch we just fixed (e.g. a new receiver format added in
   * code, or a URL pattern V53/V54 didn't anticipate). Returns the per-bucket count so the caller
   * can confirm what actually changed before the next click trigger fires.
   */
  @PostMapping("/webhooks/redetect-formats")
  public WebhookReDetectResult redetectWebhookFormats() {
    return reDetectWebhooks.execute();
  }

  /**
   * Per-route count / p50 / p95 / p99 / error rate over the requested window, sourced from the
   * {@code request_metrics} table. Replaces {@link #routeMetrics(String)} once the in-process ring
   * buffer is retired in the follow-up PR.
   */
  @GetMapping("/metrics/routes")
  public List<AdminRequestMetricsService.RouteAggregate> requestRouteMetrics(
      @RequestParam(name = "window", required = false) String window) {
    return requestMetricsService.routes(AdminRequestMetricsService.Window.parse(window));
  }

  /**
   * Outcome distribution for a single {@code shortCode} over the requested window — answers "what
   * proportion of clicks on this code ended up as redirect / not_found / expired / blocked".
   */
  @GetMapping("/metrics/outcomes")
  public AdminRequestMetricsService.OutcomeDistribution requestOutcomeDistribution(
      @RequestParam(name = "shortCode") String shortCode,
      @RequestParam(name = "window", required = false) String window) {
    return requestMetricsService.outcomes(
        shortCode, AdminRequestMetricsService.Window.parse(window));
  }

  /**
   * Single-snapshot view of what Micrometer / Actuator already collects — JVM (heap / threads /
   * GC), HikariCP pool, and per-cache hit/miss. Zero hot-path cost: gauges are read on demand when
   * the admin opens this endpoint, not measured from request traffic.
   */
  @GetMapping("/metrics/system")
  public AdminSystemMetricsService.SystemMetrics systemMetrics() {
    return systemMetricsService.snapshot();
  }

  /**
   * signup → first link → first click → first webhook user-count funnel for the requested signup
   * window. Conversion ratios surface where the pipeline narrows — onboarding (signup→link),
   * distribution (link→click), or retention (click→webhook).
   */
  @GetMapping("/funnel")
  public AdminFunnelService.Funnel funnel(
      @RequestParam(name = "window", required = false) String window) {
    return funnelService.snapshot(AdminFunnelService.Window.parse(window));
  }

  /**
   * Raw rows for incident drill-down. Filters compose: any combination of route / outcome /
   * shortCode / userId narrows the scan; from/to bound the time window (default last 1h). {@code
   * limit} is capped server-side; for deeper history pull a wider window.
   */
  @GetMapping("/metrics/requests")
  public List<AdminRequestMetricsService.RawRow> requestRawRows(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String route,
      @RequestParam(required = false) String outcome,
      @RequestParam(required = false) String shortCode,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Integer limit) {
    return requestMetricsService.raw(
        new AdminRequestMetricsService.RawQuery(
            from, to, route, outcome, shortCode, userId, limit));
  }
}
