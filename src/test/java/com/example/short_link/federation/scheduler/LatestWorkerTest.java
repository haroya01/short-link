package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.federation.application.trends.LatestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatestWorkerTest {

  private final LatestPosts latest = mock(LatestPosts.class);

  private static LatestProperties props(boolean enabled, boolean pull) {
    return new LatestProperties(
        enabled, List.of("a.example"), 40, 0, null, null, pull, Duration.ofMillis(10), 0, 0);
  }

  private static boolean threadAlive() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(thread -> thread.getName().equals("kurl-latest"));
  }

  @Test
  void theThreadDoesNotStartWhereTheLatestPullingOrSchedulingAreOff() {
    for (LatestWorker worker :
        List.of(
            new LatestWorker(latest, props(false, true), true),
            new LatestWorker(latest, props(true, false), true),
            new LatestWorker(latest, props(true, true), false))) {
      worker.start();
      assertThat(worker.isRunning()).isTrue();
      assertThat(threadAlive()).isFalse();
      worker.stop();
      assertThat(worker.isRunning()).isFalse();
    }
    verifyNoInteractions(latest);
  }

  @Test
  void itsOwnThreadPullsAgainAfterAFailureUntilStopped() throws InterruptedException {
    when(latest.pull()).thenThrow(new IllegalStateException("boom")).thenReturn(0);
    LatestWorker worker = new LatestWorker(latest, props(true, true), true);

    worker.start();
    verify(latest, timeout(5_000).atLeast(3)).pull();
    worker.stop();

    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (threadAlive() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(threadAlive()).isFalse();
  }
}
