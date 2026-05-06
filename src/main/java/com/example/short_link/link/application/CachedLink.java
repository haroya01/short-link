package com.example.short_link.link.application;

import java.time.Instant;

public record CachedLink(String originalUrl, Instant expiresAt) {

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }
}
