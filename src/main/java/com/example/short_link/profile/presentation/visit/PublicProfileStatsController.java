package com.example.short_link.profile.presentation.visit;

import com.example.short_link.profile.application.visit.ProfileStats;
import com.example.short_link.profile.application.visit.ProfileStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Missing users and private stats both return 404 to avoid exposing whether an opted-out profile
 * exists.
 */
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileStatsController {

  private final ProfileStatsService stats;

  @GetMapping("/{username}/stats")
  public ProfileStats stats(@PathVariable String username) {
    return stats.publicStats(username);
  }
}
