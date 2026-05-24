package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.CampaignArchivedException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수동 종료 — 운영자가 endsAt 전에 또는 후에 명시적으로 종료. ARCHIVED 거부, ENDED 멱등 (정책 재적용 효과). */
@Service
@RequiredArgsConstructor
public class EndCampaignNowUseCase {

  private final CampaignOwnership ownership;
  private final BatchPolicyApplier batchPolicyApplier;

  @Transactional
  public CampaignEntity execute(Long id, Long ownerId) {
    CampaignEntity c = ownership.require(id, ownerId);
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignArchivedException();
    }
    Instant now = Instant.now();
    if (c.getStatus() == CampaignStatus.ENDED) {
      batchPolicyApplier.apply(c, c.getEndedAt() != null ? c.getEndedAt() : now);
      return c;
    }
    c.markEnded(now);
    batchPolicyApplier.apply(c, now);
    return c;
  }
}
