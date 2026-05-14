package com.example.short_link.profile.visit;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous-readable profile visit stats. Returns 404 ({@code UserNotFoundException}) when the user
 * doesn't exist OR hasn't opted into public stats — the visibility flip is the only thing that
 * exposes this endpoint per-username, so the existence of an opted-out profile isn't leaked.
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
