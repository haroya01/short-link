package com.example.short_link.post.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.post.application.write.RoomPostErasures;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomPostErasureJob {

  private static final String LOCK_KEY = "kurl:post:room-erasure";

  private final RoomPostErasures erasures;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.post.room-erasure-cron:45 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      return;
    }
    try {
      int erased = erasures.eraseDue();
      if (erased > 0) {
        log.info("room posts erased: {}", erased);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
