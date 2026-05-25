package com.example.short_link.link.og.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.properties.OgFetchProperties;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.application.LinkOgFetchListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-scrapes OG metadata for links whose previous fetch succeeded but is now older than {@code
 * stale-after-days}. Source pages can swap titles/descriptions/images over time; without this job
 * the preview cards would drift further from the real page each year. Distinct from {@link
 * OgFetchRetryJob} which only handles links that never fetched successfully.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OgRefreshJob {

  private static final String LOCK_KEY = "kurl:og-fetch:refresh";
  private static final int BATCH_SIZE = 50;

  private final LinkRepository linkRepository;
  private final LinkOgFetchListener listener;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final OgFetchProperties ogFetch;

  @Scheduled(cron = "${short-link.og-fetch.refresh-cron:0 0 5 * * SUN}", zone = "Asia/Seoul")
  public void runWeekly() {
    if (!ogFetch.refreshEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(30))) {
      log.debug("og refresh skipped — lock held");
      return;
    }
    try {
      Instant before = Instant.now().minus(Duration.ofDays(ogFetch.staleAfterDays()));
      List<LinkEntity> candidates =
          linkRepository.findStaleOgCandidates(before, PageRequest.of(0, BATCH_SIZE));
      log.info("og refresh: {} stale candidates", candidates.size());
      for (LinkEntity link : candidates) {
        try {
          listener.fetchAndStore(link.getShortCode(), link.getOriginalUrl());
        } catch (RuntimeException e) {
          log.warn("og refresh failed for {}", link.getShortCode(), e);
        }
      }
      meterRegistry.counter("short_link.og_fetch.refresh").increment(candidates.size());
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
