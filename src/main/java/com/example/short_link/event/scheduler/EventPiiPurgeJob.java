package com.example.short_link.event.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.event.application.write.PurgeEventPiiUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPiiPurgeJob {

  private static final String LOCK_KEY = "kurl:event:pii-purge";

  private final PurgeEventPiiUseCase purgePii;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;

  @Scheduled(cron = "${short-link.event.pii-purge-cron:0 20 4 * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      return;
    }
    try {
      int purged = purgePii.execute(Instant.now());
      if (purged > 0) {
        log.info("event pii purge: {} registrations purged", purged);
        meterRegistry.counter("event.pii_purged").increment(purged);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
