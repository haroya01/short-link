package com.example.short_link.profile.visit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-facing endpoints for profile visit stats. Mirrors the per-link {@code
 * /api/v1/links/{shortCode}/stats} shape so the frontend dashboard can reuse its chart components
 * against either dataset.
 */
@RestController
@RequestMapping("/api/v1/users/me/profile")
@RequiredArgsConstructor
public class MyProfileStatsController {

  private final ProfileStatsService service;

  @GetMapping("/stats")
  public ProfileStats stats(@AuthenticationPrincipal Long userId) {
    return service.stats(userId);
  }

  @GetMapping("/stats/summary")
  public ProfileStatsService.ProfileVisitSummary summary(@AuthenticationPrincipal Long userId) {
    return service.summary(userId);
  }
}
