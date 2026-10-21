package com.example.short_link.federation.application.trends;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.seen")
public record SeenProperties(int maxBuffered, Duration keepFor) {

  public SeenProperties {
    maxBuffered = maxBuffered <= 0 ? 50_000 : maxBuffered;
    keepFor =
        keepFor == null || keepFor.isNegative() || keepFor.isZero() ? Duration.ofDays(14) : keepFor;
  }
}
