package com.example.short_link.user.application.twofactor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.twofa")
public record TwoFactorProperties(String aesKey, String issuer) {

  public TwoFactorProperties {
    if (aesKey == null) aesKey = "";
    if (issuer == null || issuer.isBlank()) issuer = "kurl.me";
  }
}
