package com.example.short_link.campaign.application;

import com.example.short_link.common.lock.RedisDistributedLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Campaign 상태 자동 전환 스케줄러. 매 분 시작 시각이 도래한 DRAFT 캠페인을 ACTIVE 로 옮긴다. ACTIVE → ENDED 전환 + postEndAction
 * 일괄 적용은 M6 에서 별도 핸들러로 처리한다 (도메인 행동이 더 크고 별 책임).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignLifecycleJob {

  private static final String LOCK_KEY = "kurl:campaign:lifecycle";

  private final CampaignService service;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;

  @Value("${short-link.campaign.lifecycle-enabled:true}")
  private boolean enabled;

  @Scheduled(cron = "${short-link.campaign.lifecycle-cron:0 * * * * *}", zone = "Asia/Seoul")
  public void tick() {
    if (!enabled) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofSeconds(50))) {
      log.debug("campaign lifecycle tick skipped — lock held");
      return;
    }
    try {
      Instant now = Instant.now();
      int activated = service.activateReady(now);
      if (activated > 0) {
        log.info("campaign lifecycle: activated {} campaigns", activated);
        meterRegistry
            .counter("short_link.campaign.lifecycle", "transition", "draft_to_active")
            .increment(activated);
      }
      int ended = service.endDue(now);
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
