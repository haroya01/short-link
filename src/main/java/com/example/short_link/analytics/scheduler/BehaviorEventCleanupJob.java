package com.example.short_link.analytics.scheduler;

import com.example.short_link.analytics.BehaviorAnalyticsProperties;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * behavior_event 원본을 보존 기간(기본 90일)으로 걷는다. 방침이 "원본은 90일 뒤 삭제"를 약속하므로 이 잡이 곧 컴플라이언스다 — 집계가 필요해지면 원본이
 * 살아 있는 동안 언제든 재계산하면 된다(그래서 롤업보다 청소가 먼저 존재한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorEventCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:behavior-event";

  private final BehaviorEventRepository repository;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final BehaviorAnalyticsProperties properties;

  @Scheduled(
      cron = "${short-link.behavior-analytics.cleanup-cron:0 50 4 * * *}",
      zone = "Asia/Seoul")
  @Transactional
  public void runDaily() {
    if (!properties.cleanupEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(5))) {
      log.debug("behavior-event cleanup skipped — lock held");
      return;
    }
    try {
      int removed = sweep();
      log.info(
          "behavior-event cleanup removed {} rows older than {} days",
          removed,
          properties.retentionDays());
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  int sweep() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(properties.retentionDays()));
    int removed = repository.deleteByOccurredAtBefore(cutoff);
    meterRegistry.counter("cleanup.behavior_event").increment(removed);
    return removed;
  }
}
