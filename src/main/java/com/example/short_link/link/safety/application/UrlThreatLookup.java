package com.example.short_link.link.safety.application;

import java.util.List;
import java.util.Set;

// Outages may return an allow-through verdict; lookup failures remain provider-independent
// exceptions.
public interface UrlThreatLookup {

  // Lookup failures remain exceptions so callers never cache them as a known-safe verdict.
  boolean isSafe(String fullUrl);

  // The URLs among `urls` that match a threat. An open circuit is a failure here, not an
  // allow-through:
  // a rescan that cannot ask must not count as having found nothing.
  Set<String> unsafeAmong(List<String> urls);
}
