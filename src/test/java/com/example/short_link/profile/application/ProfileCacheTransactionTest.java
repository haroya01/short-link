package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ProfileCacheTransactionTest {
  @Autowired private ProfileCacheEviction eviction;
  @Autowired private CacheManager cacheManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void cachedProfileSurvivesUntilItsWriteCommits() {
    String username = UUID.randomUUID().toString();
    Cache cache = cacheManager.getCache("public-profile");
    assertThat(cache.putIfAbsent(username, "before")).isNull();
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                eviction.evictByUsername(username);
                assertThat(cache.get(username, String.class)).isEqualTo("before");
              });
      assertThat(cache.get(username)).isNull();
    } finally {
      cache.evictIfPresent(username);
    }
  }

  @Test
  void rollbackKeepsTheCachedCommittedProfile() {
    String username = UUID.randomUUID().toString();
    Cache cache = cacheManager.getCache("public-profile");
    assertThat(cache.putIfAbsent(username, "before")).isNull();
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                eviction.evictByUsername(username);
                status.setRollbackOnly();
              });
      assertThat(cache.get(username, String.class)).isEqualTo("before");
    } finally {
      cache.evictIfPresent(username);
    }
  }
}
