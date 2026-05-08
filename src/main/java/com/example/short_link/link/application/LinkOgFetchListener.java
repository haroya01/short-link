package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link LinkOgFetchRequested} events and fetches OG metadata in the background. Runs
 * after commit so the link row is visible. Cache eviction at the end forces redirect/preview paths
 * to see the freshly written OG fields.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkOgFetchListener {

  private final OgScraper scraper;
  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;
  private final CacheManager cacheManager;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLinkCreated(LinkOgFetchRequested event) {
    fetchAndStore(event.shortCode(), event.originalUrl());
  }

  @Transactional
  public void fetchAndStore(String shortCode, String originalUrl) {
    OgMetadata og = scraper.fetch(originalUrl);
    LinkEntity entity = repository.findByShortCode(shortCode).orElse(null);
    if (entity == null) {
      return;
    }
    Instant now = Instant.now();
    if (og.hasAny()) {
      entity.applyOgMetadata(og.title(), og.description(), og.image(), now);
      repository.save(entity);
      meterRegistry.counter("short_link.og_fetch", "result", "ok").increment();
    } else {
      entity.markOgFetchFailed(now);
      repository.save(entity);
      meterRegistry.counter("short_link.og_fetch", "result", "empty").increment();
    }
    Cache cache = cacheManager.getCache("link");
    if (cache != null) {
      cache.evict(shortCode);
    }
  }
}
