package com.example.short_link.link.safety.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.safety-rescan")
public record LinkSafetyRescanProperties(boolean enabled, int batchSize) {

  public LinkSafetyRescanProperties {
    if (batchSize <= 0) {
      batchSize = 2000;
    }
  }
}
