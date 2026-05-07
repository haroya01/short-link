package com.example.short_link.admin.api;

import com.example.short_link.admin.application.AdminHealthMetrics;
import com.example.short_link.admin.application.AdminHealthService;
import com.example.short_link.admin.application.AdminOverview;
import com.example.short_link.admin.application.AdminOverviewService;
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

  @GetMapping("/overview")
  public AdminOverview overview() {
    return service.overview();
  }

  @GetMapping("/health-metrics")
  public AdminHealthMetrics healthMetrics() {
    return healthService.metrics();
  }

  @GetMapping("/recent-errors")
  public List<RecentError> recentErrors(@RequestParam(required = false) Integer limit) {
    return recentErrorsService.recent(limit);
  }
}
