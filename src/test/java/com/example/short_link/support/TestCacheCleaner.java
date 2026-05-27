package com.example.short_link.support;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

public final class TestCacheCleaner {

  private TestCacheCleaner() {}

  public static void clear(CacheManager cacheManager, String... cacheNames) {
    for (String cacheName : cacheNames) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
      }
    }
  }
}
