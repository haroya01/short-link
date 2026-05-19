package com.example.short_link.common.geoip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GeoIpRefreshJobTest {

  @Mock private GeoIpDatabaseHolder holder;
  @Mock private RedisDistributedLock lock;

  private SimpleMeterRegistry meterRegistry;
  private GeoIpRefreshJob job;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    job = new GeoIpRefreshJob(holder, lock, meterRegistry);
    ReflectionTestUtils.setField(job, "enabled", true);
    ReflectionTestUtils.setField(job, "licenseKey", "");
    ReflectionTestUtils.setField(job, "overrideUrl", "");
  }

  @Test
  void disabledSkipsEverything() {
    ReflectionTestUtils.setField(job, "enabled", false);
    job.refreshWeekly();
    verify(lock, never()).tryAcquire(anyString(), any(Duration.class));
  }

  @Test
  void emptyConfigSkipsEverything() {
    job.refreshWeekly();
    verify(lock, never()).tryAcquire(anyString(), any(Duration.class));
  }

  @Test
  void lockNotAcquiredSkipsDownload() {
    ReflectionTestUtils.setField(job, "licenseKey", "k");
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);
    job.refreshWeekly();
    verify(holder, never()).set(any());
  }

  @Test
  void downloadFailureRecordsFailureMetricAndReleasesLock() {
    ReflectionTestUtils.setField(job, "licenseKey", "k");
    ReflectionTestUtils.setField(
        job, "overrideUrl", "https://invalid-host-for-test.example.invalid/x.tar.gz");
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    job.refreshWeekly();
    assertThat(meterRegistry.counter("geoip.refresh", "result", "failed").count()).isEqualTo(1.0);
    verify(lock).release(anyString());
  }
}
