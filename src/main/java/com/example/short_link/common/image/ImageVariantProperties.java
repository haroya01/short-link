package com.example.short_link.common.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.image-variants")
public record ImageVariantProperties(
    boolean enabled, int queue, int recentRows, boolean backfillEnabled) {

  public ImageVariantProperties {
    queue = queue <= 0 ? 100 : queue;
    recentRows = recentRows <= 0 ? 200 : recentRows;
  }
}
