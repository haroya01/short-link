package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.dto.AdminActiveUsers;
import com.example.short_link.admin.application.dto.AdminActivity;
import com.example.short_link.admin.application.dto.AdminCohort;
import com.example.short_link.admin.application.dto.AdminHealthMetrics;
import com.example.short_link.admin.application.dto.AdminLifecycle;
import com.example.short_link.admin.application.dto.AdminLinkDetail;
import com.example.short_link.admin.application.dto.AdminLinkMetric;
import com.example.short_link.admin.application.dto.AdminOverview;
import com.example.short_link.admin.application.dto.AdminRouteMetric;
import com.example.short_link.admin.application.dto.AdminUserRow;
import com.example.short_link.admin.application.dto.BlogAdminMetrics;
import com.example.short_link.admin.application.dto.RecentError;
import com.example.short_link.admin.application.read.AdminActivityService;
import com.example.short_link.admin.application.read.AdminAnalyticsService;
import com.example.short_link.admin.application.read.AdminBlogMetricsService;
import com.example.short_link.admin.application.read.AdminBrowseService;
import com.example.short_link.admin.application.read.AdminHealthService;
import com.example.short_link.admin.application.read.AdminLinkMetricsQueryService;
import com.example.short_link.admin.application.read.AdminOverviewService;
import com.example.short_link.admin.application.read.AdminRouteMetricsService;
import com.example.short_link.admin.application.read.RecentErrorsService;
import com.example.short_link.common.observability.AdminFunnelService;
import com.example.short_link.common.observability.AdminRequestMetricsService;
import com.example.short_link.common.observability.AdminSystemMetricsService;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.application.dto.WebhookReDetectResult;
import com.example.short_link.link.webhook.application.write.ReDetectWebhookFormatsUseCase;
import com.example.short_link.user.application.dto.MintedAccessToken;
import com.example.short_link.user.application.write.MintAccessTokenUseCase;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final AdminBlogMetricsService blogMetricsService;
  private final AdminBrowseService browseService;
  private final AdminActivityService activityService;
  private final AdminRouteMetricsService routeMetricsService;
  private final AdminLinkMetricsQueryService linkMetricsService;
  private final ReDetectWebhookFormatsUseCase reDetectWebhooks;
  private final AdminRequestMetricsService requestMetricsService;
  private final AdminSystemMetricsService systemMetricsService;
  private final AdminFunnelService funnelService;
  private final MintAccessTokenUseCase mintAccessToken;

  /**
   * Mint a fresh access token for the calling admin — a convenience for scripting against the API
   * (seeding, one-off automation). The token carries the admin's own role only.
   */
  @PostMapping("/access-token")
  public MintedAccessToken mintAccessToken(@AuthenticationPrincipal Long userId) {
    return mintAccessToken.mintFor(userId);
  }

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

  /**
   * Full user-table browse. {@code q} matches email / handle case-insensitively; {@code role}
   * filters by USER / ADMIN; newest first. Page size is capped server-side.
   */
  @GetMapping("/users")
  public AdminBrowseService.UsersPage users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return browseService.users(q, role, page, size);
  }

  @GetMapping("/users/{id}")
  public AdminUserRow user(@PathVariable long id) {
    return browseService.user(id);
  }

  /**
   * Full link-table browse. {@code q} matches an exact short code or a substring of the destination
   * URL; {@code ownerId} narrows to one user's links (anonymous links have no owner); {@code sort}
   * is {@code recent} (default, newest first) or {@code clicks} (busiest first).
   */
  @GetMapping("/links")
  public AdminBrowseService.LinksPage links(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return browseService.links(q, ownerId, sort, page, size);
  }

  /**
   * Live activity feed for the console: newest links and clicks across all users plus the links
   * trending in the last 24h. Cheap enough to poll; click rows are PII-minimal (country / referrer
   * host / device class only — never IP or visitor hash). Declared before {@code /links/{code}} so
   * the literal path wins the match.
   */
  @GetMapping("/links/activity")
  public AdminActivity linkActivity() {
    return activityService.activity();
  }

  /**
   * One link's full metadata (owner, lifecycle, protection) plus the owner-grade click report —
   * daily / hourly / referrer / device / country breakdowns — for support and observability. Reuses
   * the owner stats assembler with the ownership check skipped.
   */
  @GetMapping("/links/{code}")
  public AdminLinkDetail linkDetail(@PathVariable ShortCode code) {
    return browseService.linkDetail(code);
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
   * Cross-author blog health — lifetime post / read totals, authors active in the last 30 days, the
   * unresolved-report backlog, and the most-read posts. 5-minute cached like the other admin
   * aggregates.
   */
  @GetMapping("/blog/metrics")
  public BlogAdminMetrics blogMetrics() {
    return blogMetricsService.metrics();
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
