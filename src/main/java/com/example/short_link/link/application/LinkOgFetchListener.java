package com.example.short_link.link.application;

import com.example.short_link.link.application.dto.LinkOgFetchRequested;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
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
 * Fetches OG metadata for new/updated links in the background. Failures are flagged RETRYABLE so a
 * scheduled job can pick them up later, until {@code max-attempts} is reached and the link is
 * stamped ERROR for good.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkOgFetchListener {

  private final OgScraper scraper;
  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;
  private final CacheManager cacheManager;
  private final OgFetchProperties ogFetch;

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
      boolean willRetry = entity.getOgFetchAttempts() + 1 < ogFetch.maxAttempts();
      entity.markOgFetchFailed(now, willRetry);
      repository.save(entity);
      meterRegistry
          .counter("short_link.og_fetch", "result", willRetry ? "retryable" : "error")
          .increment();
    }
    Cache cache = cacheManager.getCache("link");
    if (cache != null) {
      cache.evict(shortCode);
    }
  }
}
