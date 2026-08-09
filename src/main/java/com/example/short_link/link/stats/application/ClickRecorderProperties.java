package com.example.short_link.link.stats.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Buffering knobs for click recording. Capacity bounds memory during a DB outage + click spike;
 * batch size bounds a single flush transaction.
 */
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
