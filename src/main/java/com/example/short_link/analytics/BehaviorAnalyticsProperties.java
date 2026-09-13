package com.example.short_link.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 원본 보존 기간은 개인정보처리방침과 일치해야 한다. */
@ConfigurationProperties(prefix = "short-link.behavior-analytics")
public record BehaviorAnalyticsProperties(long retentionDays, boolean cleanupEnabled) {

  public BehaviorAnalyticsProperties {
    if (retentionDays <= 0) retentionDays = 90;
  }
}
