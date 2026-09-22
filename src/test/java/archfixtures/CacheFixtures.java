package archfixtures;

import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CacheEvict;

/** Breaks the cache eviction rules on purpose so the rules can be shown to catch it. */
public final class CacheFixtures {

  private CacheFixtures() {}

  public static class EvictsByAnnotation {
    @CacheEvict("link")
    public void run() {}
  }

  public static class EvictsDirectly {
    public void run(Cache cache) {
      cache.evict("key");
    }
  }

  public static class EvictsIfPresent {
    public void run(Cache cache) {
      cache.evictIfPresent("key");
    }
  }
}
