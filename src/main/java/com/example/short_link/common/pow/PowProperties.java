package com.example.short_link.common.pow;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.pow")
public record PowProperties(int difficulty, boolean enforce) {

  public PowProperties {
    if (difficulty < 1 || difficulty > 8) {
      throw new IllegalArgumentException("pow difficulty must be 1..8 (hex zeros)");
    }
  }
}
