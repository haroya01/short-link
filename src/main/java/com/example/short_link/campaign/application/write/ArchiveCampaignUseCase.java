package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveCampaignUseCase {

  private final CampaignOwnership ownership;

  @Transactional
  public CampaignEntity execute(Long id, Long ownerId) {
    CampaignEntity c = ownership.require(id, ownerId);
    c.archive();
    return c;
  }
}
