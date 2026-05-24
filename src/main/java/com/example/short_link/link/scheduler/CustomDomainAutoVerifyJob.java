package com.example.short_link.link.scheduler;

import com.example.short_link.link.application.CustomDomainService;
import com.example.short_link.link.domain.CustomDomainEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls DNS for newly-registered custom domains so the user doesn't have to babysit a Verify
 * button. Each tick walks all unverified rows created within {@link
 * CustomDomainService#AUTO_VERIFY_WINDOW} and runs one TXT lookup per row; verified rows fall out
 * naturally on the next tick. Beyond the window we stop probing — the manual /verify endpoint is
 * the fallback for slow propagation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomDomainAutoVerifyJob {

  private final CustomDomainService service;

  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void run() {
    List<CustomDomainEntity> pending = service.findPendingWithinWindow();
    if (pending.isEmpty()) return;
    int verified = 0;
    for (CustomDomainEntity entity : pending) {
      try {
        if (service.autoVerifyOne(entity)) verified++;
      } catch (RuntimeException e) {
        log.warn("auto-verify failed for {}: {}", entity.getDomain(), e.getMessage());
      }
    }
    if (verified > 0) {
      log.info("auto-verified {} of {} pending custom domains", verified, pending.size());
    }
  }
}
