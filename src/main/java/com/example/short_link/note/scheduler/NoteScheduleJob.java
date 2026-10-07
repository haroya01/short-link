package com.example.short_link.note.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NoteScheduleService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteScheduleJob {

  private static final String LOCK_KEY = "kurl:note:schedule";

  private final NoteScheduleService schedules;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.note.schedule-cron:15 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      return;
    }
    try {
      int published = schedules.publishDue();
      if (published > 0) {
        log.info("scheduled notes published: {}", published);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
