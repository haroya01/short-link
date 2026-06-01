package com.example.short_link.post.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.post.application.write.PublishScheduledPostsUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Per-minute auto-publish for scheduled posts. Mirrors {@code CampaignLifecycleJob}: a distributed
 * lock keeps a single instance doing the work in a multi-node deploy, and the actual transition
 * lives in the use case. Cron is configurable (default every minute, KST).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishScheduledPostsJob {

  private static final String LOCK_KEY = "kurl:post:scheduled-publish";

  private final PublishScheduledPostsUseCase publishScheduled;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;

  @Scheduled(cron = "${short-link.post.scheduled-publish-cron:0 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      log.debug("scheduled-post publish tick skipped — lock held");
      return;
    }
    try {
      int published = publishScheduled.execute(Instant.now());
      if (published > 0) {
        log.info("scheduled-post publish: published {} due posts", published);
        meterRegistry.counter("short_link.post.scheduled_publish").increment(published);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
