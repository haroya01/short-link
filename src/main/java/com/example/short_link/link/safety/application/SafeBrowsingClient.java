package com.example.short_link.link.safety.application;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SafeBrowsingClient {

  static final String CACHE_NAME = "safebrowsing";

  private final UrlThreatLookup lookup;

  /**
   * Retains the Boolean cache format. Cache hits bypass the lookup entirely; failures are never
   * cached. A lookup's allow-through result is cached just like a safe verdict.
   */
  @Cacheable(value = CACHE_NAME, key = "#cacheKey")
  public boolean isSafeForKey(String cacheKey, String fullUrl) {
    return lookup.isSafe(fullUrl);
  }
}
