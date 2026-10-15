package com.example.short_link.blogimport.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.blog-import")
public record BlogImportProperties(
    RobotsPolicy robotsPolicy, int syncBatch, Duration syncSpacing, Duration hostWait) {

  public enum RobotsPolicy {
    RESPECT,
    NAMED_ONLY,
    IGNORE
  }

  public BlogImportProperties {
    if (robotsPolicy == null) {
      robotsPolicy = RobotsPolicy.RESPECT;
    }
    if (syncBatch <= 0) {
      syncBatch = 60;
    }
    if (syncSpacing == null || syncSpacing.isNegative()) {
      syncSpacing = Duration.ofSeconds(2);
    }
    if (hostWait == null || hostWait.isNegative() || hostWait.isZero()) {
      hostWait = Duration.ofSeconds(20);
    }
  }
}
