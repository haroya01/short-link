package com.example.short_link.user.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.user.application.UserDeletionService;
import com.example.short_link.user.application.properties.UserDeletionProperties;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hard-deletes users whose {@code deleted_at} is older than the configured grace window. Runs
 * daily, fenced by a Redis lock so a single instance does the sweep when multiple are deployed.
 * Each user's links / click_events / api_keys / link_tags cascade away via existing FK constraints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftDeletedUserCleanupJob {

  private static final String LOCK_KEY = "kurl:cleanup:soft-deleted-users";
  private static final int BATCH_SIZE = 200;

  private final UserRepository userRepository;
  private final UserDeletionService userDeletionService;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final UserDeletionProperties userDeletion;

  @Scheduled(cron = "${short-link.user-deletion.cleanup-cron:0 30 5 * * *}", zone = "Asia/Seoul")
  public void runDaily() {
    if (!userDeletion.cleanupEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(15))) {
      log.debug("soft-deleted user cleanup skipped — lock held");
      return;
    }
    try {
      Instant cutoff = Instant.now().minus(Duration.ofDays(userDeletion.graceDays()));
      List<UserEntity> candidates = userRepository.findTop200ByDeletedAtBefore(cutoff);
      log.info(
          "soft-deleted user cleanup: {} candidates older than {} days",
          candidates.size(),
          userDeletion.graceDays());
      for (UserEntity user : candidates) {
        try {
          userDeletionService.hardDelete(user.getId());
        } catch (RuntimeException e) {
          log.warn("hard-delete failed for user {}", user.getId(), e);
        }
      }
      meterRegistry.counter("cleanup.user_hard_deleted").increment(candidates.size());
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
