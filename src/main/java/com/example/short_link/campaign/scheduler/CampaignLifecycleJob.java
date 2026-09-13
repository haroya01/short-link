package com.example.short_link.campaign.scheduler;

import com.example.short_link.campaign.application.properties.CampaignProperties;
import com.example.short_link.campaign.application.write.ActivateReadyCampaignsUseCase;
import com.example.short_link.campaign.application.write.EndDueCampaignsUseCase;
import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignLifecycleJob {

  private static final String LOCK_KEY = "kurl:campaign:lifecycle";

  private final ActivateReadyCampaignsUseCase activateUseCase;
  private final EndDueCampaignsUseCase endDueUseCase;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final CampaignProperties campaign;

  @Scheduled(cron = "${short-link.campaign.lifecycle-cron:0 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!campaign.lifecycleEnabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      log.debug("campaign lifecycle tick skipped — lock held");
      return;
    }
    try {
      Instant now = Instant.now();
      int activated = activateUseCase.execute(now);
      if (activated > 0) {
        log.info("campaign lifecycle: activated {} campaigns", activated);
        meterRegistry
            .counter("short_link.campaign.lifecycle", "transition", "draft_to_active")
            .increment(activated);
      }
      int ended = endDueUseCase.execute(now);
      if (ended > 0) {
        log.info("campaign lifecycle: ended {} campaigns (postEndAction applied)", ended);
        meterRegistry
            .counter("short_link.campaign.lifecycle", "transition", "active_to_ended")
            .increment(ended);
      }
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
