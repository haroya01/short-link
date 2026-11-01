package com.example.short_link.analytics.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.analytics.BehaviorAnalyticsProperties;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BehaviorEventCleanupJobTest {

  private static final String LOCK_KEY = "kurl:cleanup:behavior-event";

  @Mock private BehaviorEventRepository repository;
  @Mock private RedisDistributedLock lock;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private BehaviorEventCleanupJob job(boolean enabled) {
    return new BehaviorEventCleanupJob(
        repository, lock, meters, new BehaviorAnalyticsProperties(90, enabled));
  }

  // 방침이 약속한 "원본 90일" — 컷오프가 정확히 보존일수만큼 뒤인지가 이 테스트의 존재 이유다.
  @Test
  void sweepDeletesBeyondRetentionAndReportsCount() {
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    when(repository.deleteByOccurredAtBefore(cutoff.capture())).thenReturn(17);

    Instant before = Instant.now();
    int removed = job(true).sweep();

    assertThat(removed).isEqualTo(17);
    assertThat(cutoff.getValue())
        .isBetween(
            before.minus(Duration.ofDays(90)).minusSeconds(5),
            Instant.now().minus(Duration.ofDays(90)).plusSeconds(5));
  }

  @Test
  void runDailySweepsUnderTheLockAndReleasesIt() {
    when(lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(5))).thenReturn(true);
    when(repository.deleteByOccurredAtBefore(any())).thenReturn(3);

    job(true).runDaily();

    verify(repository).deleteByOccurredAtBefore(any());
    verify(lock).release(LOCK_KEY);
    assertThat(meters.counter("cleanup.behavior_event").count()).isEqualTo(3.0);
  }

  @Test
  void runDailyLeavesRowsAloneWhileAnotherNodeHoldsTheLock() {
    when(lock.tryAcquire(eq(LOCK_KEY), any())).thenReturn(false);

    job(true).runDaily();

    verifyNoInteractions(repository);
    verify(lock, never()).release(anyString());
  }

  @Test
  void runDailyDoesNothingWhenCleanupIsTurnedOff() {
    job(false).runDaily();

    verifyNoInteractions(lock, repository);
  }

  @Test
  void runDailyReleasesTheLockWhenTheDeleteFails() {
    when(lock.tryAcquire(eq(LOCK_KEY), any())).thenReturn(true);
    when(repository.deleteByOccurredAtBefore(any()))
        .thenThrow(new IllegalStateException("db down"));

    assertThatThrownBy(() -> job(true).runDaily()).isInstanceOf(IllegalStateException.class);

    verify(lock).release(LOCK_KEY);
  }

  @Test
  void invalidRetentionFallsBackToDefault() {
    assertThat(new BehaviorAnalyticsProperties(0, true).retentionDays()).isEqualTo(90);
  }
}
