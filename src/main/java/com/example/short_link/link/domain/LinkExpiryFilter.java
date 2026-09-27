package com.example.short_link.link.domain;

public enum LinkExpiryFilter {
  ALL,
  NEVER,
  ACTIVE,
  EXPIRED,
  HAS_EXPIRY,
  EXPIRING_SOON
}
