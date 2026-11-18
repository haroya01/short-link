package com.example.short_link.federation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.short_link.federation.application.trends.TrendingPosts;
import com.example.short_link.federation.application.trends.TrendingTopics;
import com.example.short_link.federation.application.trends.TrendsProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendsWorkerTest {

  private final TrendingPosts trending = mock(TrendingPosts.class);
  private final TrendingTopics topics = mock(TrendingTopics.class);

  private static TrendsProperties props(boolean enabled) {
    return new TrendsProperties(
        enabled, List.of("a.example"), 20, 1, Duration.ofMillis(10), Duration.ofSeconds(1));
  }

  private static boolean threadAlive() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(thread -> thread.getName().equals("kurl-trends"));
  }

  @Test
  void theThreadDoesNotStartWhereTrendsOrSchedulingAreOff() {
    for (TrendsWorker worker :
        List.of(
            new TrendsWorker(trending, topics, props(false), true),
            new TrendsWorker(trending, topics, props(true), false))) {
      worker.start();
      assertThat(worker.isRunning()).isTrue();
      assertThat(threadAlive()).isFalse();
      worker.stop();
      assertThat(worker.isRunning()).isFalse();
    }
    verifyNoInteractions(trending, topics);
  }

  @Test
  void itsOwnThreadRefreshesAgainAfterAFailureUntilStopped() throws InterruptedException {
    doThrow(new IllegalStateException("boom")).doNothing().when(trending).refresh();
    doNothing().doThrow(new IllegalStateException("boom")).doNothing().when(topics).refresh();
    TrendsWorker worker = new TrendsWorker(trending, topics, props(true), true);

    worker.start();
    verify(trending, timeout(5_000).atLeast(3)).refresh();
    verify(topics, timeout(5_000).atLeast(3)).refresh();
    worker.stop();

    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (threadAlive() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    assertThat(threadAlive()).isFalse();
    verify(trending, atLeast(3)).refresh();
    verify(topics, atLeast(3)).refresh();
  }
}
