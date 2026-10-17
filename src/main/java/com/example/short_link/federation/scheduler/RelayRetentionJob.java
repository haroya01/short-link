package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.relay.RelayTally;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The shared scheduler has a single thread, so a run with pauses goes to one low-priority thread of
// its own. It runs whatever the relay setting, so notes kept before a relay was turned off still
// go.
@Slf4j
@Component
public class RelayRetentionJob {

  static final Duration KEEP_FOR = Duration.ofDays(7);
  static final int BATCH = 200;
  private static final Duration PAUSE = Duration.ofSeconds(1);

  private final FederationRelayedNoteRepository relayed;
  private final RelayTally tally;
  private final Clock clock;
  private final Duration pause;
  private final ExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public RelayRetentionJob(
      FederationRelayedNoteRepository relayed,
      RelayTally tally,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this(relayed, tally, Clock.systemUTC(), PAUSE, scheduling);
  }

  RelayRetentionJob(
      FederationRelayedNoteRepository relayed,
      RelayTally tally,
      Clock clock,
      Duration pause,
      boolean scheduling) {
    this.relayed = relayed;
    this.tally = tally;
    this.clock = clock;
    this.pause = pause;
    this.executor =
        scheduling
            ? Executors.newSingleThreadExecutor(
                work ->
                    Thread.ofPlatform()
                        .name("kurl-relay-retention")
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .unstarted(work))
            : null;
  }

  @Scheduled(
      cron = "${short-link.federation.relay.retention-cron:0 20 4 * * *}",
      zone = "Asia/Seoul")
  public void scheduled() {
    if (executor == null || !running.compareAndSet(false, true)) {
      return;
    }
    executor.execute(
        () -> {
          try {
            sweep();
          } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
          } catch (RuntimeException failed) {
            log.warn("federation relay retention failed reason={}", failed.toString());
          } finally {
            running.set(false);
          }
        });
  }

  public int sweep() throws InterruptedException {
    long started = System.nanoTime();
    Instant before = clock.instant().minus(KEEP_FOR);
    int deleted = 0;
    int batches = 0;
    while (true) {
      int batch = relayed.deleteExpired(before, BATCH);
      deleted += batch;
      batches++;
      if (batch < BATCH) {
        break;
      }
      TimeUnit.NANOSECONDS.sleep(pause.toNanos());
    }
    tally.count("expired", deleted);
    log.info(
        "federation relay retention deleted={} batches={} tookMs={}",
        deleted,
        batches,
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    return deleted;
  }

  @PreDestroy
  void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
