package com.example.short_link.link.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.og-fetch")
public record OgFetchProperties(int maxAttempts, long staleAfterDays, boolean refreshEnabled) {

  public OgFetchProperties {
    if (maxAttempts <= 0) maxAttempts = 3;
    if (staleAfterDays <= 0) staleAfterDays = 30;
  }
}
