package com.example.short_link.admin.application.read;

import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import com.example.short_link.common.transaction.AfterCommit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link BlockedDomainQueryService} 내부 호출에도 Spring 캐시 프록시가 적용되도록 분리한다. */
@Component
@RequiredArgsConstructor
public class BlockedDomainCache {

  public static final String CACHE_NAME = "blocked-domains";
  public static final String CACHE_KEY = "all";

  private final BlockedDomainRepository repository;
  private final CacheManager cacheManager;

  @Cacheable(value = CACHE_NAME, key = "'" + CACHE_KEY + "'")
  @Transactional(readOnly = true)
  public BlockedDomains currentBlockedSet() {
    return new BlockedDomains(repository.findAllDomains());
  }

  public void evictAfterCommit() {
    AfterCommit.run(this::evictNow);
  }

  public void evictNow() {
    Cache cache = cacheManager.getCache(CACHE_NAME);
    if (cache != null) {
      cache.evictIfPresent(CACHE_KEY);
    }
  }

  public record BlockedDomains(List<String> domains) {

    public BlockedDomains {
      domains = domains == null ? List.of() : List.copyOf(domains);
    }

    @JsonIgnore
    public boolean isEmpty() {
      return domains.isEmpty();
    }

    @JsonIgnore
    public boolean contains(String domain) {
      return domains.contains(domain);
    }
  }
}
