package com.example.short_link.profile.application;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Targeted invalidation for the {@code public-profile} cache. The 11 write paths that previously
 * used {@code @CacheEvict(allEntries = true)} all flushed every user's cached profile on a single
 * user's mutation — a quiet O(N) bill on every avatar upload / block edit. Cache key is the
 * normalized username (see ProfileQueryService); evicting the one key preserves the rest.
 *
 * <p>This wave migrates AvatarService / BannerService (4 sites, user already loaded). OgOverride +
 * 6 profile/write use cases (UpdateBlock, CreateBlock, DeleteBlock, ReorderProfile,
 * SetLinkHighlight, ToggleLinkOnProfile, UpdateProfile) follow in a separate wave — each has a
 * different load pattern (link.userId / cmd.userId / block.userId / user load) so mechanical
 * migration isn't safe in bulk.
 */
@Service
@RequiredArgsConstructor
public class ProfileCacheEviction {

  static final String CACHE_NAME = "public-profile";

  private final CacheManager cacheManager;
  private final UserRepository userRepository;

  public void evictByUsername(String username) {
    if (username == null || username.isBlank()) return;
    Cache cache = cacheManager.getCache(CACHE_NAME);
    if (cache == null) return;
    cache.evict(username.trim().toLowerCase());
  }

  public void evictByUserId(Long userId) {
    if (userId == null) return;
    userRepository.findById(userId).map(UserEntity::getUsername).ifPresent(this::evictByUsername);
  }
}
