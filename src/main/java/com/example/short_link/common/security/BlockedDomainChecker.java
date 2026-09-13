package com.example.short_link.common.security;

/**
 * Admin provides the blocklist implementation through this port to avoid an admin/link dependency
 * cycle.
 */
public interface BlockedDomainChecker {
  boolean isBlocked(String url);
}
