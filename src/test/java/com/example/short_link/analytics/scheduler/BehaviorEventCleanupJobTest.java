package com.example.short_link.analytics.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.analytics.BehaviorAnalyticsProperties;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BehaviorEventCleanupJobTest {

  @Mock private BehaviorEventRepository repository;
  @Mock private RedisDistributedLock lock;

  // 방침이 약속한 "원본 90일" — 컷오프가 정확히 보존일수만큼 뒤인지가 이 테스트의 존재 이유다.
  @Test
  void sweepDeletesBeyondRetentionAndReportsCount() {
    BehaviorEventCleanupJob job =
        new BehaviorEventCleanupJob(
            repository, lock, new SimpleMeterRegistry(), new BehaviorAnalyticsProperties(90, true));
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    when(repository.deleteByOccurredAtBefore(cutoff.capture())).thenReturn(17);

    Instant before = Instant.now();
    int removed = job.sweep();

    assertThat(removed).isEqualTo(17);
    assertThat(cutoff.getValue())
        .isBetween(
            before.minus(java.time.Duration.ofDays(90)).minusSeconds(5),
            Instant.now().minus(java.time.Duration.ofDays(90)).plusSeconds(5));
  }

  @Test
  void invalidRetentionFallsBackToDefault() {
    assertThat(new BehaviorAnalyticsProperties(0, true).retentionDays()).isEqualTo(90);
  }
}
