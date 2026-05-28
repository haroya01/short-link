package com.example.short_link.link.export.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.export")
public record LinkExportProperties(int eventBatch, int eventCap) {

  public LinkExportProperties {
    if (eventBatch <= 0) eventBatch = 1000;
    if (eventCap <= 0) eventCap = 50000;
  }
}
