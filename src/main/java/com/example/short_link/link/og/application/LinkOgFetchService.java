package com.example.short_link.link.og.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.application.write.LinkSidecars;
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
  private final LinkSidecars sidecars;
  private final MeterRegistry meterRegistry;
  private final LinkCacheEviction linkCacheEviction;
  private final OgFetchProperties ogFetch;

  // 페이지 조회는 트랜잭션 밖에서 하고, 저장은 저장소별 트랜잭션으로 한다.
  public void refresh(ShortCode shortCode, String originalUrl) {
    OgMetadata og = scraper.fetch(originalUrl);
    LinkEntity entity = repository.findByShortCode(shortCode).orElse(null);
    if (entity == null) {
      return;
    }
    Instant now = Instant.now();
    if (og.hasAny()) {
      sidecars.recordOgFetched(entity, og.title(), og.description(), og.image(), now);
      repository.recordOgFetched(entity.getId(), og.title(), og.description(), og.image(), now);
      meterRegistry.counter("short_link.og_fetch", "result", "ok").increment();
    } else {
      boolean willRetry = entity.getOgFetchAttempts() + 1 < ogFetch.maxAttempts();
      sidecars.recordOgFetchFailed(entity, now, willRetry);
      repository.recordOgFetchFailed(entity.getId(), now, willRetry);
      meterRegistry
          .counter("short_link.og_fetch", "result", willRetry ? "retryable" : "error")
          .increment();
    }
    linkCacheEviction.evictAfterCommit(shortCode);
  }
}
