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
 * Picks up links whose initial OG fetch failed (status = {@code PENDING} or {@code RETRYABLE}) and
 * retries them — bounded by {@code max-attempts}. Runs slightly after the cleanup job to avoid
 * fighting for DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OgFetchRetryJob {

  private static final String LOCK_KEY = "kurl:og-fetch:retry";
  private static final int BATCH_SIZE = 100;

  private final LinkRepository linkRepository;
  private final LinkOgFetchListener listener;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final OgFetchProperties ogFetch;

  @Scheduled(cron = "${short-link.og-fetch.retry-cron:0 30 4 * * *}", zone = "Asia/Seoul")
  public void runDaily() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(20))) {
      log.debug("og fetch retry skipped — lock held");
      return;
    }
    try {
      Instant before = Instant.now().minus(Duration.ofHours(1));
      List<LinkEntity> candidates =
          linkRepository.findOgRetryCandidates(
              ogFetch.maxAttempts(), before, PageRequest.of(0, BATCH_SIZE));
      log.info("og fetch retry: {} candidates", candidates.size());
      for (LinkEntity link : candidates) {
        try {
          listener.fetchAndStore(link.getShortCode(), link.getOriginalUrl());
        } catch (RuntimeException e) {
          log.warn("og fetch retry failed for {}", link.getShortCode(), e);
        }
      }
      meterRegistry.counter("short_link.og_fetch.retry").increment(candidates.size());
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
