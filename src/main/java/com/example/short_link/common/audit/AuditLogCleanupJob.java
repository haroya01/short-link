package com.example.short_link.common.audit;

import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trims the audit_log table to a sliding retention window. The table grows with every privileged
 * action so we drop rows older than {@code retention-days} on a daily cadence; this is the only
 * cleanup path — everything else stays append-only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:audit-log";

  private final AuditLogRepository repository;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;

  @Value("${short-link.audit-log.retention-days:180}")
  private long retentionDays;

  @Value("${short-link.audit-log.cleanup-enabled:true}")
  private boolean enabled;

  @Scheduled(cron = "${short-link.audit-log.cleanup-cron:0 45 4 * * *}", zone = "Asia/Seoul")
  public void runDaily() {
    if (!enabled) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(5))) {
      log.debug("audit-log cleanup skipped — lock held");
      return;
    }
    try {
      int removed = sweep();
      log.info("audit-log cleanup removed {} rows older than {} days", removed, retentionDays);
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  @Transactional
  public int sweep() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
    int removed = repository.deleteByOccurredAtBefore(cutoff);
    meterRegistry.counter("cleanup.audit_log").increment(removed);
    return removed;
  }
}
