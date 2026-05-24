package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignRepository;
import com.example.short_link.campaign.exception.CampaignNotFoundException;
import com.example.short_link.campaign.exception.CampaignNotOwnedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CampaignOwnership {

  private final CampaignRepository repository;

  CampaignEntity require(Long id, Long ownerId) {
    CampaignEntity c = repository.findById(id).orElseThrow(CampaignNotFoundException::new);
    if (!c.isOwnedBy(ownerId)) {
      throw new CampaignNotOwnedException();
    }
    return c;
  }
}
