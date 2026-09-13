package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.dto.AdminActiveUsers;
import com.example.short_link.admin.application.dto.AdminCohort;
import com.example.short_link.admin.application.dto.AdminLifecycle;
import com.example.short_link.admin.application.dto.AdminOverview;
import com.example.short_link.admin.application.dto.BlogAdminMetrics;
import com.example.short_link.admin.application.read.AdminAnalyticsService;
import com.example.short_link.admin.application.read.AdminBlogMetricsService;
import com.example.short_link.admin.application.read.AdminOverviewService;
import com.example.short_link.common.observability.AdminFunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

  private final AdminOverviewService service;
  private final AdminAnalyticsService analyticsService;
  private final AdminBlogMetricsService blogMetricsService;
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

  @GetMapping("/blog/metrics")
  public BlogAdminMetrics blogMetrics() {
    return blogMetricsService.metrics();
  }

  @GetMapping("/funnel")
  public AdminFunnelService.Funnel funnel(
      @RequestParam(name = "window", required = false) String window) {
    return funnelService.snapshot(AdminFunnelService.Window.parse(window));
  }
}
