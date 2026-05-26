package com.example.short_link.common.security;

/**
 * Cross-slice port for the operator-managed blocklist. Link-create callers ask "is this URL
 * blocked?" without importing the admin domain — admin owns the implementation, link only sees this
 * interface. Breaks the admin↔link slice cycle that LinkId migration surfaced.
 */
public interface BlockedDomainChecker {
  boolean isBlocked(String url);
}
