package com.example.short_link.link.access.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.turnstile")
public record TurnstileProperties(String siteKey, String secret) {

  public TurnstileProperties {
    siteKey = siteKey == null ? "" : siteKey.trim();
    secret = secret == null ? "" : secret.trim();
  }

  public boolean widgetEnabled() {
    return !siteKey.isBlank();
  }

  public boolean verifyEnabled() {
    return !secret.isBlank();
  }
}
