package com.example.short_link.note.scheduler;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.short_link.note.application.write.NoteLinkPreviewRefill;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

@Timeout(30)
class NoteLinkPreviewRefillJobTest {

  private final NoteLinkPreviewRefill refill = mock(NoteLinkPreviewRefill.class);
  private final CountDownLatch started = new CountDownLatch(1);
  private final CountDownLatch release = new CountDownLatch(1);
  private final CountDownLatch finished = new CountDownLatch(1);
  private final AtomicReference<Thread> ranOn = new AtomicReference<>();
  private final AtomicBoolean interrupted = new AtomicBoolean();

  NoteLinkPreviewRefillJobTest() {
    doAnswer(
            invocation -> {
              ranOn.set(Thread.currentThread());
              started.countDown();
              try {
                release.await();
              } catch (InterruptedException stopped) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
              }
              finished.countDown();
              return null;
            })
        .when(refill)
        .step();
  }

  @Test
  void aTickHandsTheStepToTheRefillThreadAndDropsTicksWhileItRuns() throws Exception {
    NoteLinkPreviewRefillJob job = new NoteLinkPreviewRefillJob(refill);
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(2), job::step);
      assertThat(started.await(5, SECONDS)).isTrue();
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 5; i++) {
              job.step();
            }
          });
      release.countDown();
      assertThat(finished.await(5, SECONDS)).isTrue();
      Thread.sleep(200);

      verify(refill, times(1)).step();
      assertThat(ranOn.get().getName()).startsWith("note-link-refill-");
      assertThat(ranOn.get().isDaemon()).isTrue();
    } finally {
      job.destroy();
    }
  }

  @Test
  void closingTheAppInterruptsTheRunningStepAndEndsTheRefillThread() {
    AtomicReference<NoteLinkPreviewRefillJob> job = new AtomicReference<>();
    AtomicReference<Duration> closing = new AtomicReference<>();

    new ApplicationContextRunner()
        .withBean(NoteLinkPreviewRefill.class, () -> refill)
        .withBean(NoteLinkPreviewRefillJob.class)
        .run(
            context -> {
              job.set(context.getBean(NoteLinkPreviewRefillJob.class));
              job.get().step();
              assertThat(started.await(5, SECONDS)).isTrue();
              long began = System.nanoTime();
              context.close();
              closing.set(Duration.ofNanos(System.nanoTime() - began));
            });

    assertThat(closing.get()).isLessThan(Duration.ofSeconds(2));
    assertThat(interrupted).isTrue();
    assertThat(ranOn.get().isAlive()).isFalse();
    job.get().step();
    verify(refill, times(1)).step();
  }

  @Test
  void theSharedSchedulerTicksAtLeastASecondAfterTheLastTickEnded() throws Exception {
    Scheduled tick =
        NoteLinkPreviewRefillJob.class.getMethod("step").getAnnotation(Scheduled.class);

    assertThat(Duration.parse(tick.fixedDelayString()))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    assertThat(tick.fixedRateString()).isEmpty();
  }
}
