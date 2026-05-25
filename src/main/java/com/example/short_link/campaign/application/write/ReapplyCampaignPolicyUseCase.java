package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종료 정책 재적용 — ENDED 후 정책 변경 (postEndAction / postEndDestinationUrl) 시 명시적 액션으로 다시 박는다. */
@Service
@RequiredArgsConstructor
public class ReapplyCampaignPolicyUseCase {

  private final CampaignOwnership ownership;
  private final BatchPolicyApplier batchPolicyApplier;

  @Transactional
  public CampaignEntity execute(Long id, Long ownerId) {
    CampaignEntity c = ownership.require(id, ownerId);
    if (c.getStatus() != CampaignStatus.ENDED) {
      throw new CampaignException(CampaignErrorCode.REAPPLY_ON_NON_ENDED);
    }
    batchPolicyApplier.apply(c, c.getEndedAt() != null ? c.getEndedAt() : Instant.now());
    return c;
  }
}
