package com.example.short_link.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 행동 이벤트 원본의 보존 계약 — 개인정보처리방침이 약속한 기간(기본 90일)을 코드가 지키는 자리다. */
@ConfigurationProperties(prefix = "short-link.behavior-analytics")
public record BehaviorAnalyticsProperties(long retentionDays, boolean cleanupEnabled) {

  public BehaviorAnalyticsProperties {
    if (retentionDays <= 0) retentionDays = 90;
  }
}
