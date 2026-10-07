package com.example.short_link.portability.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.portability.application.AccountImportService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountImportJob {

  static final String LOCK_KEY = "kurl:portability:import";
  static final int BATCH = 50;

  private final AccountImportService imports;
  private final RedisDistributedLock lock;

  @Scheduled(cron = "${short-link.portability.import-cron:*/10 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(9))) {
      return;
    }
    try {
      imports.processBatch(BATCH);
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
