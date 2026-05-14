package com.example.short_link.profile.visit;

import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing profile visit stats + the public-visibility toggle. */
@RestController
@RequestMapping("/api/v1/users/me/profile")
@RequiredArgsConstructor
public class MyProfileStatsController {

  private final ProfileStatsService stats;
  private final UserRepository userRepository;

  @GetMapping("/stats")
  @PreAuthorize("isAuthenticated()")
  public ProfileStats stats(@AuthenticationPrincipal Long userId) {
    return stats.statsForOwner(userId);
  }

  @GetMapping("/stats/summary")
  @PreAuthorize("isAuthenticated()")
  public ProfileVisitSummary summary(@AuthenticationPrincipal Long userId) {
    return stats.summaryForOwner(userId);
  }

  @GetMapping("/stats/visibility")
  @PreAuthorize("isAuthenticated()")
  public StatsVisibilityResponse getVisibility(@AuthenticationPrincipal Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    return new StatsVisibilityResponse(user.isStatsPublic());
  }

  @PatchMapping("/stats/visibility")
  @PreAuthorize("isAuthenticated()")
  public StatsVisibilityResponse setVisibility(
      @AuthenticationPrincipal Long userId, @RequestBody StatsVisibilityRequest body) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    user.updateStatsPublic(body.isPublic());
    userRepository.save(user);
    return new StatsVisibilityResponse(user.isStatsPublic());
  }

  public record StatsVisibilityRequest(boolean isPublic) {}

  public record StatsVisibilityResponse(boolean isPublic) {}
}
