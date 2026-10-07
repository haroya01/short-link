package com.example.short_link.note.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.note.application.write.NotePollService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotePollCloseJob {

  private static final String LOCK_KEY = "kurl:note:poll-close";

  private final NotePollService polls;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.note.poll-close-cron:30 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      return;
    }
    try {
      int closed = polls.closeDue();
      if (closed > 0) {
        log.info("note polls closed: {}", closed);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
