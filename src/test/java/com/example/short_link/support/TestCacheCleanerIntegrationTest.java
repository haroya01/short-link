package com.example.short_link.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class TestCacheCleanerIntegrationTest {

  private static final String CACHE = "cache-cleaner-probe";

  @Autowired private CacheManager cacheManager;

  @Test
  void aClearedCacheHasNoEntryOnTheVeryNextRead() {
    Cache cache = cacheManager.getCache(CACHE);
    for (int round = 0; round < 100; round++) {
      String key = "stale-" + round;
      cache.put(key, "a link that was deleted");
      await().atMost(Duration.ofSeconds(5)).until(() -> cache.get(key) != null);

      TestCacheCleaner.clear(cacheManager, CACHE);

      assertThat(cache.get(key)).as("round %d", round).isNull();
    }
  }
}
