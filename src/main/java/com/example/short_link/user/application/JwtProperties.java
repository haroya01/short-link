package com.example.short_link.user.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.jwt")
public record JwtProperties(
    String privateKey, String publicKey, Duration accessTtl, Duration refreshTtl) {

  public JwtProperties {
    if (privateKey == null) privateKey = "";
    if (publicKey == null) publicKey = "";
  }
}
