package com.example.short_link.link.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.stats.application.ClickRetentionProperties;
import com.example.short_link.link.stats.application.ClickRollupService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickRollupJob {

  private static final String LOCK_KEY = "kurl:cleanup:click-rollup";

  private final ClickRollupService rollup;
  private final ClickRetentionProperties properties;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  @Scheduled(cron = "${short-link.click-retention.cron:0 20 4 * * *}", zone = "Asia/Seoul")
  public void runDaily() {
    if (!properties.rollupEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(30))) {
      log.debug("click rollup skipped — lock held");
      return;
    }
    try {
      long started = System.nanoTime();
      Instant now = clock.instant();
      int days = rollup.rollUp(now);
      long deleted = rollup.deleteRolledUp();
      int summed = rollup.aggregate(now);
      meterRegistry.counter("cleanup.click_rollup_days").increment(days);
      meterRegistry.counter("cleanup.click_event").increment(deleted);
      meterRegistry.counter("cleanup.click_recent_days").increment(summed);
      log.info(
          "click rollup folded {} days, deleted {} raw clicks and summed {} recent days in {} ms",
          days,
          deleted,
          summed,
          (System.nanoTime() - started) / 1_000_000);
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
