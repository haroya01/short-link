package com.example.short_link.link.og.application;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches OG metadata for new/updated links in the background. Failures are flagged RETRYABLE so a
 * scheduled job can pick them up later, until {@code max-attempts} is reached and the link is
 * stamped ERROR for good.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkOgFetchService {

  private final OgScraper scraper;
  private final LinkRepository repository;
  private final LinkOgMetadataRepository ogMetadataRepository;
  private final MeterRegistry meterRegistry;
  private final CacheManager cacheManager;
  private final OgFetchProperties ogFetch;

  /** AFTER_COMMIT의 비동기 이벤트 경로: 저장소별 기존 트랜잭션을 유지한다. */
  public void fetchAfterCommit(ShortCode shortCode, String originalUrl) {
    fetchAndStore(shortCode, originalUrl);
  }

  /** 스케줄러 경로는 이전과 같이 조회·저장을 하나의 트랜잭션으로 실행한다. */
  @Transactional
  public void refresh(ShortCode shortCode, String originalUrl) {
    fetchAndStore(shortCode, originalUrl);
  }

  private void fetchAndStore(ShortCode shortCode, String originalUrl) {
    OgMetadata og = scraper.fetch(originalUrl);
    LinkEntity entity = repository.findByShortCode(shortCode).orElse(null);
    if (entity == null) {
      return;
    }
    Instant now = Instant.now();
    LinkOgMetadataEntity ogMeta =
        ogMetadataRepository
            .findById(entity.getId())
            .orElseGet(() -> new LinkOgMetadataEntity(entity.linkId()));
    if (og.hasAny()) {
      entity.applyOgMetadata(og.title(), og.description(), og.image(), now);
      ogMeta.applyFetched(og.title(), og.description(), og.image(), now);
      repository.save(entity);
      ogMetadataRepository.save(ogMeta);
      meterRegistry.counter("short_link.og_fetch", "result", "ok").increment();
    } else {
      boolean willRetry = entity.getOgFetchAttempts() + 1 < ogFetch.maxAttempts();
      entity.markOgFetchFailed(now, willRetry);
      ogMeta.markFetchFailed(now, willRetry);
      repository.save(entity);
      ogMetadataRepository.save(ogMeta);
      meterRegistry
          .counter("short_link.og_fetch", "result", willRetry ? "retryable" : "error")
          .increment();
    }
    Cache cache = cacheManager.getCache("link");
    if (cache != null) {
      cache.evict(shortCode.value());
    }
  }
}
