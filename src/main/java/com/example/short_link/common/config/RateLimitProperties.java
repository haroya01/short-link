package com.example.short_link.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.rate-limit")
public record RateLimitProperties(long anonymous, long authenticated) {

  public RateLimitProperties {
    if (anonymous <= 0) anonymous = 100;
    if (authenticated <= 0) authenticated = 1000;
  }
}
