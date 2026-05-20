package com.example.short_link.common.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuditLogCleanupJobDisabledTest {

  private AuditLogRepository repository;
  private RedisDistributedLock lock;
  private AuditLogCleanupJob job;

  @BeforeEach
  void setUp() throws Exception {
    repository = Mockito.mock(AuditLogRepository.class);
    lock = Mockito.mock(RedisDistributedLock.class);
    job = new AuditLogCleanupJob(repository, lock, new SimpleMeterRegistry());
    setField("retentionDays", 180L);
    setField("enabled", true);
  }

  private void setField(String name, Object value) throws Exception {
    Field f = AuditLogCleanupJob.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(job, value);
  }

  @Test
  void disabledSkipsLockAndSweep() throws Exception {
    setField("enabled", false);
    job.runDaily();
    verify(lock, never()).tryAcquire(anyString(), any(Duration.class));
    verify(repository, never()).deleteByOccurredAtBefore(any());
  }

  @Test
  void lockHeldByOtherInstanceSkipsSweep() {
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);
    job.runDaily();
    verify(repository, never()).deleteByOccurredAtBefore(any());
  }

  @Test
  void releasesLockEvenWhenSweepThrows() {
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    when(repository.deleteByOccurredAtBefore(any())).thenThrow(new RuntimeException("db down"));

    try {
      job.runDaily();
    } catch (RuntimeException ignored) {
      // expected
    }

    verify(lock, times(1)).release("kurl:cleanup:audit-log");
  }
}
