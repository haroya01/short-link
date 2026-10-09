package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.link.application.write.DedicatedLinks;
import com.example.short_link.link.domain.LinkId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CampaignDedicatedLinks implements DedicatedLinks {

  private final JpaCampaignBatchRepository batches;

  @Override
  public boolean isDedicated(LinkId linkId) {
    return batches.existsByLinkId(linkId.value());
  }
}
