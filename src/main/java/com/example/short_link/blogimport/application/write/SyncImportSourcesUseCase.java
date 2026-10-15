package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.common.lock.RedisDistributedLock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncImportSourcesUseCase {

  static final String LOCK_KEY = "kurl:blog-import:sync";
  static final Duration LOCK_TTL = Duration.ofMinutes(25);
  static final Duration SYNC_INTERVAL = Duration.ofMinutes(25);
  static final Duration STALLED_AFTER = Duration.ofMinutes(30);
  static final int BATCH = 100;

  private final ImportSourceRepository sources;
  private final BlogImportRunner runner;
  private final RedisDistributedLock lock;
  private final Clock clock;

  @Async("blogImportExecutor")
  public void execute() {
    if (!lock.tryAcquire(LOCK_KEY, LOCK_TTL)) {
      return;
    }
    try {
      runDue();
    } finally {
      lock.release(LOCK_KEY);
    }
  }

  int runDue() {
    Instant now = clock.instant();
    int ran = 0;
    for (Long sourceId :
        sources.findDueIds(now.minus(SYNC_INTERVAL), now.minus(STALLED_AFTER), BATCH)) {
      try {
        runner.run(sourceId);
        ran++;
      } catch (RuntimeException e) {
        log.warn("blog import sync failed source={}", sourceId, e);
      }
    }
    return ran;
  }
}
