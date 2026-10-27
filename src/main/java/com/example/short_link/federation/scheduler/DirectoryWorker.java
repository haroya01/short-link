package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.directory.DirectoryProperties;
import com.example.short_link.federation.application.directory.DirectorySync;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

// A run takes minutes of paced requests, so it goes to one low-priority thread of its own rather
// than the shared scheduler thread; a run asked for while one is going is not started. No thread
// exists where directories or scheduling are off. A restart can cut a run short, so a while after
// start a run is made when a source has gone unread since the cron's last turn.
@Slf4j
@Component
public class DirectoryWorker {

  static final String CRON = "${short-link.federation.directory.cron:0 40 4,16 * * *}";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Duration AFTER_START = Duration.ofMinutes(2);

  private final DirectorySync sync;
  private final CronExpression cron;
  private final Duration afterStart;
  private final Clock clock;
  private final ScheduledExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public DirectoryWorker(
      DirectorySync sync,
      DirectoryProperties props,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling,
      @Value(CRON) String cron,
      Clock clock) {
    this(sync, props, scheduling, cron, AFTER_START, clock);
  }

  DirectoryWorker(
      DirectorySync sync,
      DirectoryProperties props,
      boolean scheduling,
      String cron,
      Duration afterStart,
      Clock clock) {
    this.sync = sync;
    this.cron = CronExpression.parse(cron);
    this.afterStart = afterStart;
    this.clock = clock;
    this.executor =
        props.enabled() && scheduling
            ? Executors.newSingleThreadScheduledExecutor(
                work ->
                    Thread.ofPlatform()
                        .name("kurl-directory")
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .unstarted(work))
            : null;
  }

  @Scheduled(cron = CRON, zone = "Asia/Seoul")
  public void scheduled() {
    trigger();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void started() {
    if (executor != null) {
      executor.schedule(this::catchUp, afterStart.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  void catchUp() {
    try {
      Instant lastTurn = lastTurn();
      if (sync.unreadSince(lastTurn) && trigger()) {
        log.info("federation directory run after start lastTurn={}", lastTurn);
      }
    } catch (RuntimeException failed) {
      log.warn("federation directory check after start failed reason={}", failed.toString());
    }
  }

  Instant lastTurn() {
    ZonedDateTime now = ZonedDateTime.now(clock.withZone(SEOUL));
    ZonedDateTime turn = cron.next(now.minusDays(1));
    ZonedDateTime last = turn;
    while (turn != null && !turn.isAfter(now)) {
      last = turn;
      turn = cron.next(turn);
    }
    return last.toInstant();
  }

  public boolean trigger() {
    if (executor == null || !running.compareAndSet(false, true)) {
      return false;
    }
    executor.execute(
        () -> {
          try {
            sync.run();
          } catch (RuntimeException failed) {
            log.warn("federation directory run failed reason={}", failed.toString());
          } finally {
            running.set(false);
          }
        });
    return true;
  }

  public boolean running() {
    return running.get();
  }

  @PreDestroy
  void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
