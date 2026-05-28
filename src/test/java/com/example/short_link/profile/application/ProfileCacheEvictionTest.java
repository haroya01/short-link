package com.example.short_link.profile.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class ProfileCacheEvictionTest {

  private final CacheManager cacheManager = mock(CacheManager.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final ProfileCacheEviction eviction =
      new ProfileCacheEviction(cacheManager, userRepository);

  @Test
  void evictByUsernameNormalizesToLowerCase() {
    Cache cache = mock(Cache.class);
    when(cacheManager.getCache("public-profile")).thenReturn(cache);

    eviction.evictByUsername("  Alice  ");

    verify(cache).evict("alice");
  }

  @Test
  void evictByUsernameNoOpsOnBlank() {
    eviction.evictByUsername(null);
    eviction.evictByUsername("   ");
    verify(cacheManager, never()).getCache(any());
  }

  @Test
  void evictByUsernameNoOpsWhenCacheMissing() {
    when(cacheManager.getCache("public-profile")).thenReturn(null);
    eviction.evictByUsername("alice");
    // No exception, no eviction.
  }

  @Test
  void evictByUserIdLooksUpAndDelegates() {
    Cache cache = mock(Cache.class);
    when(cacheManager.getCache("public-profile")).thenReturn(cache);
    UserEntity user = mock(UserEntity.class);
    when(user.getUsername()).thenReturn("bob");
    when(userRepository.findById(42L)).thenReturn(Optional.of(user));

    eviction.evictByUserId(42L);

    verify(cache).evict("bob");
  }

  @Test
  void evictByUserIdNoOpsForNullId() {
    eviction.evictByUserId(null);
    verify(userRepository, never()).findById(any());
  }

  @Test
  void evictByUserIdNoOpsWhenUserMissing() {
    when(userRepository.findById(99L)).thenReturn(Optional.empty());
    eviction.evictByUserId(99L);
    verify(cacheManager, never()).getCache(any());
  }

  @Test
  void evictByUserIdNoOpsWhenUserHasNoUsername() {
    UserEntity user = mock(UserEntity.class);
    when(user.getUsername()).thenReturn(null);
    when(userRepository.findById(99L)).thenReturn(Optional.of(user));
    eviction.evictByUserId(99L);
    verify(cacheManager, never()).getCache(any());
  }
}
