package com.example.short_link.federation.application.trends;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.reach")
public record ReachProperties(
    boolean enabled,
    int perDay,
    int trendedPerDay,
    Duration every,
    int perRound,
    Duration pause,
    Duration patience) {

  // Mastodon answers at most 20 statuses a request to /api/v1/statuses.
  static final int PER_REQUEST = 20;

  public ReachProperties {
    perDay = perDay <= 0 ? 1_500 : perDay;
    trendedPerDay = trendedPerDay <= 0 ? 300 : trendedPerDay;
    every = every == null || every.isNegative() || every.isZero() ? Duration.ofMinutes(5) : every;
    perRound = perRound <= 0 ? 200 : perRound;
    pause = pause == null || pause.isNegative() ? Duration.ofSeconds(1) : pause;
    patience = patience == null ? Duration.ofSeconds(5) : patience;
  }
}
