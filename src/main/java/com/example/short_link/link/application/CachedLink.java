package com.example.short_link.link.application;

import java.time.Instant;

public record CachedLink(
    Long linkId,
    String originalUrl,
    Instant expiresAt,
    String ogTitle,
    String ogDescription,
    String ogImage) {

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }
}
