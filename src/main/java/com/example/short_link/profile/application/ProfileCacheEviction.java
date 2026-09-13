package com.example.short_link.profile.application;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.transaction.AfterCommit;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** public-profile 캐시 키는 정규화된 사용자명이다. 해당 키만 커밋 후 무효화한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileCacheEviction implements ProfileCacheInvalidator {

  static final String CACHE_NAME = "public-profile";

  private final CacheManager cacheManager;
  private final UserRepository userRepository;

  @Override
  public void evictByUsername(String username) {
    if (username == null || username.isBlank()) return;
    String key = username.trim().toLowerCase(Locale.ROOT);
    AfterCommit.run(() -> evictCommittedProfile(key));
  }

  private void evictCommittedProfile(String key) {
    try {
      Cache cache = cacheManager.getCache(CACHE_NAME);
      // Wait for removal so a read after commit cannot race an asynchronous Redis eviction.
      if (cache != null) cache.evictIfPresent(key);
    } catch (RuntimeException failure) {
      // 캐시 장애로 이미 커밋된 쓰기를 실패 응답으로 바꾸지 않는다.
      log.warn("public profile cache eviction failed: {}", failure.toString());
    }
  }

  @Override
  public void evictByUserId(Long userId) {
    if (userId == null) return;
    userRepository.findById(userId).map(UserEntity::getUsername).ifPresent(this::evictByUsername);
  }
}
