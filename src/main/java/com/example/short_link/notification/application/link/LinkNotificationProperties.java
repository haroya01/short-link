package com.example.short_link.notification.application.link;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.link-notification")
public record LinkNotificationProperties(
    boolean digestEnabled,
    boolean expiryEnabled,
    int expiryThresholdDays,
    int velocityMinClicks,
    double velocityRatio) {

  public LinkNotificationProperties {
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
