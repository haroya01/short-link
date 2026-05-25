package com.example.short_link.link.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.application.properties.CleanupProperties;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes long-expired links and their click events. A {@code grace} window past expiry keeps
 * recently-expired rows around so users can recover stats briefly. Fenced by a Redis lock so a
 * single task runs the job per cycle when multiple instances are deployed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredLinkCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:expired-links";

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final CacheManager cacheManager;
  private final CleanupProperties cleanup;

  @Scheduled(cron = "${short-link.cleanup.cron:0 0 4 * * *}", zone = "Asia/Seoul")
  @Transactional
  public void runDaily() {
    if (!cleanup.enabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(15))) {
      log.debug("expired link cleanup skipped — lock held by another instance");
      return;
    }
    try {
      int total = sweep();
      log.info("expired link cleanup removed {} links", total);
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  int sweep() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(cleanup.expiredGraceDays()));
    int totalLinks = 0;
    int totalClicks = 0;
    while (true) {
      List<LinkEntity> batch =
          linkRepository.findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(cutoff);
      if (batch.isEmpty()) break;
      List<Long> ids = batch.stream().map(LinkEntity::getId).toList();
      totalClicks += clickEventRepository.deleteByLinkIds(ids);
      linkRepository.deleteAll(batch);
      totalLinks += batch.size();
      evictCaches(batch);
      if (batch.size() < 500) break;
    }
    meterRegistry.counter("cleanup.expired_links", "result", "ok").increment(totalLinks);
    meterRegistry.counter("cleanup.expired_clicks", "result", "ok").increment(totalClicks);
    return totalLinks;
  }

  private void evictCaches(List<LinkEntity> batch) {
    var cache = cacheManager.getCache("link");
    if (cache == null) return;
    for (LinkEntity link : batch) {
      cache.evict(link.getShortCode());
    }
  }
}
