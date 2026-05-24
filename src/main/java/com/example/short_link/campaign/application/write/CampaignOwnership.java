package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CampaignOwnership {

  private final CampaignRepository repository;

  CampaignEntity require(Long id, Long ownerId) {
    CampaignEntity c =
        repository
            .findById(id)
            .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    if (!c.isOwnedBy(ownerId)) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
    }
    return c;
  }
}
