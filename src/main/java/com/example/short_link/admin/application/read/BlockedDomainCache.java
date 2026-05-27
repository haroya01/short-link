package com.example.short_link.admin.application.read;

import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache boundary for the full blocked-domain set. Kept outside {@link BlockedDomainQueryService} so
 * Spring's cache proxy is used even when the query service checks the set from inside {@code
 * isBlocked}.
 */
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
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              evictNow();
            }
          });
      return;
    }
    evictNow();
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
