package com.example.short_link.link.og.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkOgFetchService {

  private final OgScraper scraper;
  private final LinkRepository repository;
  private final MeterRegistry meterRegistry;
  private final LinkCacheEviction linkCacheEviction;
  private final OgFetchProperties ogFetch;

  public void refresh(ShortCode shortCode, String originalUrl) {
    OgMetadata og = scraper.fetch(originalUrl);
    LinkEntity entity = repository.findByShortCode(shortCode).orElse(null);
    if (entity == null || !entity.getOriginalUrl().equals(originalUrl)) {
      return;
    }
    Instant now = Instant.now();
    if (og.hasAny()) {
      repository.recordOgFetched(entity.getId(), og.title(), og.description(), og.image(), now);
      meterRegistry.counter("short_link.og_fetch", "result", "ok").increment();
    } else {
      boolean willRetry = entity.getOgFetchAttempts() + 1 < ogFetch.maxAttempts();
      repository.recordOgFetchFailed(entity.getId(), now, willRetry);
      meterRegistry
          .counter("short_link.og_fetch", "result", willRetry ? "retryable" : "error")
          .increment();
    }
    linkCacheEviction.evictAfterCommit(shortCode);
  }
}
