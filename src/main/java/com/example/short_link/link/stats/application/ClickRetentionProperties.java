package com.example.short_link.link.stats.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 원본 보존 기간은 개인정보처리방침과 일치해야 한다.
@ConfigurationProperties(prefix = "short-link.click-retention")
public record ClickRetentionProperties(int rawDays, boolean rollupEnabled, int deleteBatch) {

  public ClickRetentionProperties {
    if (rawDays <= 0) rawDays = 90;
    if (deleteBatch <= 0) deleteBatch = 1000;
  }
}
