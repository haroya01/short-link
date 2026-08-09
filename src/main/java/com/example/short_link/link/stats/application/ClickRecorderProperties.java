package com.example.short_link.link.stats.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.click-recorder")
public record ClickRecorderProperties(int queueCapacity, int maxBatchPerFlush) {

  public ClickRecorderProperties {
    if (queueCapacity <= 0) {
      queueCapacity = 50_000;
    }
    if (maxBatchPerFlush <= 0) {
      maxBatchPerFlush = 500;
    }
  }
}
