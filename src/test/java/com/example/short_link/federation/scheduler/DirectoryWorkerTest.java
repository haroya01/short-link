package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.directory.DirectoryProperties;
import com.example.short_link.federation.application.directory.DirectorySync;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DirectoryWorkerTest {

  private static final String CRON = "0 40 4,16 * * *";
  private static final Instant AT_1704_SEOUL = Instant.parse("2026-10-17T08:04:00Z");
  private static final Instant TURN_1640_SEOUL = Instant.parse("2026-10-17T07:40:00Z");

  private final DirectorySync sync = mock(DirectorySync.class);

  private static DirectoryProperties props(boolean enabled) {
    return new DirectoryProperties(enabled, 100, 1_000, null, null, null, null);
  }

  private DirectoryWorker worker(boolean enabled, boolean scheduling, Instant now) {
    return new DirectoryWorker(
        sync,
        props(enabled),
        scheduling,
        CRON,
        Duration.ofMillis(50),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void nothingRunsWhereDirectoriesOrSchedulingAreOff() {
    DirectoryWorker off = worker(false, true, AT_1704_SEOUL);
    DirectoryWorker unscheduled = worker(true, false, AT_1704_SEOUL);

    assertThat(off.trigger()).isFalse();
    assertThat(unscheduled.trigger()).isFalse();
    off.started();
    unscheduled.started();
    verify(sync, after(300).never()).unreadSince(any());
    verify(sync, never()).run();
  }

  @Test
  void aRunGoesToItsOwnThreadAndASecondAskWhileItGoesIsNotStarted() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    String[] thread = new String[1];
    when(sync.run())
        .thenAnswer(
            call -> {
              thread[0] = Thread.currentThread().getName();
              release.await(5, TimeUnit.SECONDS);
              return List.of();
            });
    DirectoryWorker worker = worker(true, true, AT_1704_SEOUL);

    assertThat(worker.trigger()).isTrue();
    verify(sync, timeout(2_000)).run();
    assertThat(worker.running()).isTrue();
    assertThat(worker.trigger()).isFalse();
    release.countDown();
    for (int i = 0; i < 50 && worker.running(); i++) {
      Thread.sleep(20);
    }

    assertThat(worker.running()).isFalse();
    assertThat(thread[0]).isEqualTo("kurl-directory");
    worker.stop();
  }

  @Test
  void theLastTurnIsTheCronsLatestFiringInSeoulAtOrBeforeNow() {
    assertThat(worker(true, true, AT_1704_SEOUL).lastTurn()).isEqualTo(TURN_1640_SEOUL);
    assertThat(worker(true, true, TURN_1640_SEOUL).lastTurn()).isEqualTo(TURN_1640_SEOUL);
    assertThat(worker(true, true, Instant.parse("2026-10-17T07:39:59Z")).lastTurn())
        .isEqualTo(Instant.parse("2026-10-16T19:40:00Z"));
  }

  @Test
  void aWhileAfterStartARunIsMadeWhenASourceWentUnreadSinceTheLastTurn() {
    when(sync.unreadSince(TURN_1640_SEOUL)).thenReturn(true);
    DirectoryWorker worker = worker(true, true, AT_1704_SEOUL);

    worker.started();

    verify(sync, timeout(2_000)).run();
    worker.stop();
  }

  @Test
  void noRunAfterStartWhenEverySourceWasReadSinceTheLastTurn() {
    when(sync.unreadSince(TURN_1640_SEOUL)).thenReturn(false);
    DirectoryWorker worker = worker(true, true, AT_1704_SEOUL);

    worker.started();

    verify(sync, timeout(2_000)).unreadSince(TURN_1640_SEOUL);
    verify(sync, after(300).never()).run();
    worker.stop();
  }

  @Test
  void theRunAfterStartIsNotStartedWhileARunIsGoing() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    when(sync.run())
        .thenAnswer(
            call -> {
              release.await(5, TimeUnit.SECONDS);
              return List.of();
            });
    when(sync.unreadSince(any())).thenReturn(true);
    DirectoryWorker worker = worker(true, true, AT_1704_SEOUL);
    assertThat(worker.trigger()).isTrue();

    worker.catchUp();
    release.countDown();

    verify(sync, after(300).times(1)).run();
    worker.stop();
  }
}
