package com.example.short_link.link.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.cleanup")
public record CleanupProperties(boolean enabled, long expiredGraceDays) {

  public CleanupProperties {
    if (expiredGraceDays <= 0) expiredGraceDays = 30;
  }
}
