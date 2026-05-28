package com.example.short_link.common.audit.scheduler;

import com.example.short_link.common.audit.AuditLogJpaRepository;
import com.example.short_link.common.audit.AuditLogProperties;
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
 * Trims the audit_log table to a sliding retention window. The table grows with every privileged
 * action so we drop rows older than {@code retention-days} on a daily cadence; this is the only
 * cleanup path — everything else stays append-only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:audit-log";

  private final AuditLogJpaRepository repository;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final AuditLogProperties auditLog;

  @Scheduled(cron = "${short-link.audit-log.cleanup-cron:0 45 4 * * *}", zone = "Asia/Seoul")
  @Transactional
  public void runDaily() {
    if (!auditLog.cleanupEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(5))) {
      log.debug("audit-log cleanup skipped — lock held");
      return;
    }
    try {
      int removed = sweep();
      log.info(
          "audit-log cleanup removed {} rows older than {} days",
          removed,
          auditLog.retentionDays());
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  int sweep() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(auditLog.retentionDays()));
    int removed = repository.deleteByOccurredAtBefore(cutoff);
    meterRegistry.counter("cleanup.audit_log").increment(removed);
    return removed;
  }
}
