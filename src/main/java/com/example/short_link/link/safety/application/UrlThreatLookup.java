package com.example.short_link.link.safety.application;

/**
 * Threat lookup with provider-independent failures and an allow-through result during an outage.
 */
public interface UrlThreatLookup {

  /** Lookup failures remain exceptions so callers never cache them as a known-safe verdict. */
  boolean isSafe(String fullUrl);
}
