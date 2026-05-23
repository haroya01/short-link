package com.example.short_link.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.user-deletion")
public record UserDeletionProperties(long graceDays, boolean cleanupEnabled) {

  public UserDeletionProperties {
    if (graceDays <= 0) graceDays = 30;
  }
}
