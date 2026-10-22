package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.trends.LatestProperties;
import com.example.short_link.federation.application.trends.ReachProperties;
import com.example.short_link.federation.application.trends.RemoteReach;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReachWorkerTest {

  private final RemoteReach reach = mock(RemoteReach.class);

  private static ReachProperties props(boolean enabled) {
    return new ReachProperties(enabled, 0, 0, Duration.ofMillis(10), 0, null, null);
  }

  private static LatestProperties latest(boolean pull) {
    return new LatestProperties(true, List.of("a.example"), 40, 0, null, null, pull, null, 0, 0);
  }

  private static boolean threadAlive() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(thread -> thread.getName().equals("kurl-reach"));
  }

  @Test
  void theThreadDoesNotStartWhereReachPullingOrSchedulingAreOff() {
    for (ReachWorker worker :
        List.of(
            new ReachWorker(reach, props(false), latest(true), true),
            new ReachWorker(reach, props(true), latest(false), true),
            new ReachWorker(reach, props(true), latest(true), false))) {
      worker.start();
      assertThat(worker.isRunning()).isTrue();
      assertThat(threadAlive()).isFalse();
      worker.stop();
      assertThat(worker.isRunning()).isFalse();
    }
    verifyNoInteractions(reach);
  }

  @Test
  void itsOwnThreadAsksAgainAfterAFailureUntilStopped() throws InterruptedException {
    when(reach.round()).thenThrow(new IllegalStateException("boom")).thenReturn(0);
    ReachWorker worker = new ReachWorker(reach, props(true), latest(true), true);

    worker.start();
    verify(reach, timeout(5_000).atLeast(3)).round();
    worker.stop();

    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (threadAlive() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(threadAlive()).isFalse();
  }
}
