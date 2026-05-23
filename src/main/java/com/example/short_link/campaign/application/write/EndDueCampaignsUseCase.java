package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignRepository;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 스케줄러 진입점 — ACTIVE 중 endsAt 이 도달한 것을 ENDED 로 전환 + postEndAction 일괄 적용. */
@Service
@RequiredArgsConstructor
public class EndDueCampaignsUseCase {

  private final CampaignRepository repository;
  private final BatchPolicyApplier batchPolicyApplier;

  @Transactional
  public int execute(Instant now) {
    List<CampaignEntity> due =
        repository.findByStatusAndEndsAtLessThanEqual(CampaignStatus.ACTIVE, now);
    for (CampaignEntity c : due) {
      c.markEnded(now);
      batchPolicyApplier.apply(c, now);
    }
    return due.size();
  }
}
