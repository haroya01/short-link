package com.example.short_link.admin.api;

import com.example.short_link.admin.application.AdminActiveUsers;
import com.example.short_link.admin.application.AdminAnalyticsService;
import com.example.short_link.admin.application.AdminCohort;
import com.example.short_link.admin.application.AdminHealthMetrics;
import com.example.short_link.admin.application.AdminHealthService;
import com.example.short_link.admin.application.AdminLifecycle;
import com.example.short_link.admin.application.AdminLinkMetric;
import com.example.short_link.admin.application.AdminLinkMetricsService;
import com.example.short_link.admin.application.AdminOverview;
import com.example.short_link.admin.application.AdminOverviewService;
import com.example.short_link.admin.application.AdminRouteMetric;
import com.example.short_link.admin.application.AdminRouteMetricsService;
import com.example.short_link.admin.application.RecentError;
import com.example.short_link.admin.application.RecentErrorsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final AdminLinkMetricsService linkMetricsService;

  @GetMapping("/overview")
  public AdminOverview overview() {
    return service.overview();
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
        AdminLinkMetricsService.Window.parse(window), AdminLinkMetricsService.Sort.parse(sort));
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
}
