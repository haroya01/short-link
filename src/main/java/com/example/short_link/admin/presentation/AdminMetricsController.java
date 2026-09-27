package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.dto.AdminHealthMetrics;
import com.example.short_link.admin.application.dto.AdminLinkMetric;
import com.example.short_link.admin.application.dto.AdminRouteMetric;
import com.example.short_link.admin.application.dto.RecentError;
import com.example.short_link.admin.application.read.AdminHealthService;
import com.example.short_link.admin.application.read.AdminLinkMetricsQueryService;
import com.example.short_link.admin.application.read.AdminRouteMetricsService;
import com.example.short_link.admin.application.read.RecentErrorsService;
import com.example.short_link.common.observability.AdminRequestMetricsService;
import com.example.short_link.common.observability.AdminSystemMetricsService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

  private final AdminHealthService healthService;
  private final RecentErrorsService recentErrorsService;
  private final AdminRouteMetricsService routeMetricsService;
  private final AdminLinkMetricsQueryService linkMetricsService;
  private final AdminRequestMetricsService requestMetricsService;
  private final AdminSystemMetricsService systemMetricsService;

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

  @GetMapping("/metrics/routes")
  public List<AdminRequestMetricsService.RouteAggregate> requestRouteMetrics(
      @RequestParam(name = "window", required = false) String window) {
    return requestMetricsService.routes(AdminRequestMetricsService.Window.parse(window));
  }

  @GetMapping("/metrics/outcomes")
  public AdminRequestMetricsService.OutcomeDistribution requestOutcomeDistribution(
      @RequestParam(name = "shortCode") String shortCode,
      @RequestParam(name = "window", required = false) String window) {
    return requestMetricsService.outcomes(
        shortCode, AdminRequestMetricsService.Window.parse(window));
  }

  @GetMapping("/metrics/system")
  public AdminSystemMetricsService.SystemMetrics systemMetrics() {
    return systemMetricsService.snapshot();
  }

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
