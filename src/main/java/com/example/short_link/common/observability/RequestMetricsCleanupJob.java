package com.example.short_link.common.observability;

import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trims {@code request_metrics} to a sliding retention window. These rows are operational telemetry
 * (route/status/latency, plus an admin-dashboard user link) — we keep a bounded window instead of
 * an unbounded behavioral log of every API call. Mirrors {@link
 * com.example.short_link.common.audit.scheduler.AuditLogCleanupJob}.
 */
@Slf4j
@Component
public class RequestMetricsCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:request-metrics";

  private final RequestMetricJpaRepository repository;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final int retentionDays;
  private final boolean enabled;

  public RequestMetricsCleanupJob(
      RequestMetricJpaRepository repository,
      RedisDistributedLock lock,
      MeterRegistry meterRegistry,
      @Value("${short-link.request-metrics.retention-days:90}") int retentionDays,
      @Value("${short-link.request-metrics.cleanup-enabled:true}") boolean enabled) {
    this.repository = repository;
    this.lock = lock;
    this.meterRegistry = meterRegistry;
    this.retentionDays = retentionDays;
    this.enabled = enabled;
  }

  @Scheduled(cron = "${short-link.request-metrics.cleanup-cron:0 50 4 * * *}", zone = "Asia/Seoul")
  @Transactional
  public void runDaily() {
    if (!enabled) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(5))) {
      log.debug("request-metrics cleanup skipped — lock held");
      return;
    }
    try {
      int removed =
          repository.deleteByOccurredAtBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
      meterRegistry.counter("cleanup.request_metrics").increment(removed);
      log.info(
          "request-metrics cleanup removed {} rows older than {} days", removed, retentionDays);
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
