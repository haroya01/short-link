package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.link.application.CampaignLinks;
import com.example.short_link.link.application.read.LinkRefReader;
import com.example.short_link.link.domain.LinkId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BatchPolicyApplier {

  private final CampaignBatchRepository batchRepository;
  private final LinkRefReader links;
  private final CampaignLinks campaignLinks;

  void apply(CampaignEntity c, Instant at) {
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(c.getId());
    List<LinkId> applied = new ArrayList<>();
    for (CampaignBatchEntity batch : batches) {
      LinkId linkId = batch.linkId();
      if (links.find(linkId).isEmpty()) continue;
      switch (c.getPostEndAction()) {
        case KEEP:
          break;
        case EXPIRE:
          campaignLinks.expire(linkId, at, null, c.getPostEndMessage());
          applied.add(linkId);
          break;
        case REDIRECT:
          campaignLinks.expire(linkId, at, c.getPostEndDestinationUrl(), null);
          applied.add(linkId);
          break;
      }
    }
    campaignLinks.evictAfterCommit(applied);
  }
}
