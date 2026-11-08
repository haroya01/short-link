package com.example.short_link.common.cache.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.web")
public record WebRevalidateProperties(String revalidateUrl, String revalidateSecret) {

  public WebRevalidateProperties {
    revalidateSecret = revalidateSecret == null ? "" : revalidateSecret.trim();
  }

  public boolean enabled() {
    return !revalidateSecret.isEmpty();
  }
}
