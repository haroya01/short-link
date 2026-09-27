package com.example.short_link.customdomain.scheduler;

import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.customdomain.application.write.AutoVerifyCustomDomainUseCase;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomDomainAutoVerifyJob {

  private final CustomDomainQueryService queryService;
  private final AutoVerifyCustomDomainUseCase autoVerify;

  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void run() {
    List<CustomDomainEntity> pending = queryService.findPendingWithinWindow();
    if (pending.isEmpty()) return;
    int verified = 0;
    for (CustomDomainEntity entity : pending) {
      try {
        if (autoVerify.execute(entity)) verified++;
      } catch (RuntimeException e) {
        log.warn("auto-verify failed for {}: {}", entity.getDomain(), e.getMessage());
      }
    }
    if (verified > 0) {
      log.info("auto-verified {} of {} pending custom domains", verified, pending.size());
    }
  }
}
