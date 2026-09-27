package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
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
    Instant endedAt = c.endNow(Instant.now());
    batchPolicyApplier.apply(c, endedAt);
    return c;
  }
}
