package com.example.short_link.note.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.RoomNoteErasures;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomNoteErasureJob {

  private static final String LOCK_KEY = "kurl:note:room-erasure";

  private final RoomNoteErasures erasures;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.note.room-erasure-cron:15 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      return;
    }
    try {
      int erased = erasures.eraseDue();
      if (erased > 0) {
        log.info("room notes erased: {}", erased);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
