package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.BlogImportProperties;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.common.lock.RedisDistributedLock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// Each tick takes at most syncBatch due sources, one at a time with syncSpacing between them; a
// source is due no sooner than 30 minutes after its last fetch, later while it keeps failing.
@Slf4j
@Service
public class SyncImportSourcesUseCase {

  static final String LOCK_KEY = "kurl:blog-import:sync";
  static final Duration LOCK_TTL = Duration.ofMinutes(25);
  static final Duration STALLED_AFTER = Duration.ofMinutes(30);

  private final ImportSourceRepository sources;
  private final BlogImportRunner runner;
  private final RedisDistributedLock lock;
  private final Clock clock;
  private final int batch;
  private final Duration spacing;

  public SyncImportSourcesUseCase(
      ImportSourceRepository sources,
      BlogImportRunner runner,
      RedisDistributedLock lock,
      Clock clock,
      BlogImportProperties properties) {
    this.sources = sources;
    this.runner = runner;
    this.lock = lock;
    this.clock = clock;
    this.batch = properties.syncBatch();
    this.spacing = properties.syncSpacing();
  }

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
    List<Long> due = sources.findDueIds(now, now.minus(STALLED_AFTER), batch);
    int ran = 0;
    for (Long sourceId : due) {
      if (ran > 0 && !pause()) {
        break;
      }
      try {
        runner.run(sourceId);
      } catch (RuntimeException e) {
        log.warn("blog import sync failed source={}", sourceId, e);
      }
      ran++;
    }
    return ran;
  }

  private boolean pause() {
    if (spacing.isZero()) {
      return true;
    }
    try {
      Thread.sleep(spacing.toMillis());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
