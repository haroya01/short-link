package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivateReadyCampaignsUseCase {

  private final CampaignRepository repository;

  @Transactional
  public int execute(Instant now) {
    List<CampaignEntity> ready =
        repository.findByStatusAndStartsAtLessThanEqual(CampaignStatus.DRAFT, now);
    int count = 0;
    for (CampaignEntity c : ready) {
      CampaignStatus before = c.getStatus();
      c.activateIfStarted(now);
      if (c.getStatus() != before) {
        count++;
      }
    }
    return count;
  }
}
