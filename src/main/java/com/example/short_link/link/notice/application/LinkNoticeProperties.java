package com.example.short_link.link.notice.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.link-notification")
public record LinkNoticeProperties(
    boolean digestEnabled,
    boolean expiryEnabled,
    int expiryThresholdDays,
    int velocityMinClicks,
    double velocityRatio) {

  public LinkNoticeProperties {
    if (expiryThresholdDays <= 0) {
      expiryThresholdDays = 7;
    }
    if (velocityMinClicks <= 0) {
      velocityMinClicks = 10;
    }
    if (velocityRatio <= 1.0) {
      velocityRatio = 3.0;
    }
  }
}
