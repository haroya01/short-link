package com.example.short_link.link.domain;

public enum LinkExpiryFilter {
  ALL,
  NEVER,
  ACTIVE,
  EXPIRED,
  HAS_EXPIRY,
  /** Links whose expiresAt falls in [now, now + EXPIRING_SOON_WINDOW). */
  EXPIRING_SOON
}
