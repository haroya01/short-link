package com.example.short_link.user.application.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.jwt")
public record JwtProperties(
    String privateKey,
    String publicKey,
    Duration accessTtl,
    Duration refreshTtl,
    Duration refreshRotationGrace) {

  public JwtProperties {
    if (privateKey == null) privateKey = "";
    if (publicKey == null) publicKey = "";
    // Allow brief replay after rotation so tabs/subdomains sharing a cookie do not trigger theft
    // detection.
    if (refreshRotationGrace == null) refreshRotationGrace = Duration.ofSeconds(10);
  }
}
