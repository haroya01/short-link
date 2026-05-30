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
    // Tolerance window during which a just-rotated refresh token may be replayed without tripping
    // theft detection — absorbs the cross-tab / cross-subdomain race where two origins share one
    // rotating refresh cookie and one sends the stale copy a moment after the other rotated it.
    if (refreshRotationGrace == null) refreshRotationGrace = Duration.ofSeconds(10);
  }
}
