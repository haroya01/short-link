package com.example.short_link.notification.application.link;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for owner link notifications. Cron jobs are toggleable + the velocity heuristic is
 * configurable. Sensible defaults; all overridable by env.
 */
@ConfigurationProperties(prefix = "short-link.link-notification")
public record LinkNotificationProperties(
    boolean digestEnabled,
    String digestCron,
    boolean expiryEnabled,
    String expiryCron,
    int expiryThresholdDays,
    int velocityMinClicks,
    double velocityRatio) {

  public LinkNotificationProperties {
    if (digestCron == null || digestCron.isBlank()) {
      digestCron = "0 0 8 * * *"; // 매일 08:00 KST
    }
    if (expiryCron == null || expiryCron.isBlank()) {
      expiryCron = "0 0 9 * * *"; // 매일 09:00 KST
    }
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
