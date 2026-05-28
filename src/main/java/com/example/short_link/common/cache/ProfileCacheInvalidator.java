package com.example.short_link.common.cache;

public interface ProfileCacheInvalidator {
  void evictByUsername(String username);

  void evictByUserId(Long userId);
}
