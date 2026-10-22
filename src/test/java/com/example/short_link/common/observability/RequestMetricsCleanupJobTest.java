package com.example.short_link.common.observability;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class RequestMetricsCleanupJobTest {

  @Mock private RequestMetricJpaRepository repository;
  @Mock private RedisDistributedLock lock;

  @Test
  void deletesThirtyDayOldRowsAThousandAtATimeUntilAShortBatch() {
    RequestMetricsCleanupJob job =
        new RequestMetricsCleanupJob(repository, lock, new SimpleMeterRegistry(), 30, true);
    when(lock.tryAcquire(any(), any())).thenReturn(true);
    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    when(repository.deleteBatchBefore(cutoff.capture(), eq(1000))).thenReturn(1000, 1000, 7);

    Instant before = Instant.now();
    job.runDaily();

    verify(repository, times(3)).deleteBatchBefore(any(), eq(1000));
    org.assertj.core.api.Assertions.assertThat(cutoff.getValue())
        .isBetween(
            before.minus(Duration.ofDays(30)).minusSeconds(5),
            Instant.now().minus(Duration.ofDays(30)).plusSeconds(5));
  }
}
