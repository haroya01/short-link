package com.example.short_link.link.application;

import com.example.short_link.common.transaction.AfterCommit;
import com.example.short_link.link.domain.ShortCode;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 리다이렉트가 읽는 link 캐시는 쓰기가 커밋된 뒤에 지우고, 삭제가 끝날 때까지 기다린다. 커밋 전에 지우면 그 사이 읽은 요청이 옛 행을 다시 캐시하고, {@link
 * Cache#evict}는 삭제를 기다리지 않고 반환할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkCacheEviction {

  static final String CACHE_NAME = "link";

  private final CacheManager cacheManager;

  public void evictAfterCommit(ShortCode shortCode) {
    if (shortCode == null) return;
    evictAllAfterCommit(List.of(shortCode));
  }

  public void evictAllAfterCommit(Collection<ShortCode> shortCodes) {
    if (shortCodes.isEmpty()) return;
    List<ShortCode> keys = List.copyOf(shortCodes);
    AfterCommit.run(() -> keys.forEach(this::evictCommitted));
  }

  private void evictCommitted(ShortCode shortCode) {
    try {
      Cache cache = cacheManager.getCache(CACHE_NAME);
      if (cache != null) cache.evictIfPresent(shortCode);
    } catch (RuntimeException failure) {
      // 캐시 장애로 이미 커밋된 쓰기를 실패 응답으로 바꾸지 않는다.
      log.warn("link cache eviction failed for {}: {}", shortCode, failure.toString());
    }
  }
}
