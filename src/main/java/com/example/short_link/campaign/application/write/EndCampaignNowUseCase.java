package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EndCampaignNowUseCase {

  private final CampaignOwnership ownership;
  private final BatchPolicyApplier batchPolicyApplier;

  @Transactional
  public CampaignEntity execute(Long id, Long ownerId) {
    CampaignEntity c = ownership.require(id, ownerId);
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_ARCHIVED);
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
