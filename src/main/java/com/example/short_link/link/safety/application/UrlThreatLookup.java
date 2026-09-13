package com.example.short_link.link.safety.application;

/**
 * Outages may return an allow-through verdict; lookup failures remain provider-independent
 * exceptions.
 */
public interface UrlThreatLookup {

  /** Lookup failures remain exceptions so callers never cache them as a known-safe verdict. */
  boolean isSafe(String fullUrl);
}
