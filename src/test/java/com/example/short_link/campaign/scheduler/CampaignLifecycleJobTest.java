package com.example.short_link.campaign.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.properties.CampaignProperties;
import com.example.short_link.campaign.application.write.ActivateReadyCampaignsUseCase;
import com.example.short_link.campaign.application.write.EndDueCampaignsUseCase;
import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CampaignLifecycleJobTest {

  private final ActivateReadyCampaignsUseCase activate = mock(ActivateReadyCampaignsUseCase.class);
  private final EndDueCampaignsUseCase endDue = mock(EndDueCampaignsUseCase.class);
  private final RedisDistributedLock lock = mock(RedisDistributedLock.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private CampaignLifecycleJob job(boolean enabled) {
    return new CampaignLifecycleJob(
        activate, endDue, lock, meterRegistry, new CampaignProperties(enabled));
  }

  @Test
  void skipsWhenDisabled() {
    CampaignLifecycleJob job = job(false);

    job.tick();

    verify(lock, never()).tryAcquire(anyString(), any(Duration.class));
    verify(activate, never()).execute(any());
    verify(endDue, never()).execute(any());
  }

  @Test
  void skipsWhenLockNotAcquired() {
    CampaignLifecycleJob job = job(true);
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(false);

    job.tick();

    verify(activate, never()).execute(any());
    verify(endDue, never()).execute(any());
    verify(lock, never()).release(anyString());
  }

  @Test
  void incrementsMetricsAndReleasesLockOnSuccess() {
    CampaignLifecycleJob job = job(true);
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    when(activate.execute(any())).thenReturn(3);
    when(endDue.execute(any())).thenReturn(2);

    job.tick();

    verify(activate).execute(any());
    verify(endDue).execute(any());
    verify(lock).release("kurl:campaign:lifecycle");
    var activated =
        meterRegistry.find("short_link.campaign.lifecycle").tag("transition", "draft_to_active");
    var ended =
        meterRegistry.find("short_link.campaign.lifecycle").tag("transition", "active_to_ended");
    // counters may be lazy, retrieve directly
    Assertions.assertThat(activated.counter().count()).isEqualTo(3.0);
    Assertions.assertThat(ended.counter().count()).isEqualTo(2.0);
  }

  @Test
  void zeroResultSkipsMetricIncrementButStillReleasesLock() {
    CampaignLifecycleJob job = job(true);
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    when(activate.execute(any())).thenReturn(0);
    when(endDue.execute(any())).thenReturn(0);

    job.tick();

    verify(lock).release("kurl:campaign:lifecycle");
    Assertions.assertThat(meterRegistry.find("short_link.campaign.lifecycle").counters()).isEmpty();
  }

  @Test
  void releasesLockEvenWhenUseCaseThrows() {
    CampaignLifecycleJob job = job(true);
    when(lock.tryAcquire(anyString(), any(Duration.class))).thenReturn(true);
    when(activate.execute(any())).thenThrow(new RuntimeException("boom"));

    Assertions.assertThatThrownBy(job::tick).isInstanceOf(RuntimeException.class);
    verify(lock).release("kurl:campaign:lifecycle");
  }
}
