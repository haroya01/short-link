package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies the campaign's {@code postEndAction} (KEEP / EXPIRE / REDIRECT) to every batch link.
 * Shared by EndDue / EndCampaignNow / ReapplyPolicy use cases.
 */
@Component
@RequiredArgsConstructor
class BatchPolicyApplier {

  private final CampaignBatchRepository batchRepository;
  private final LinkRepository linkRepository;

  void apply(CampaignEntity c, Instant at) {
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(c.getId());
    for (CampaignBatchEntity batch : batches) {
      LinkEntity link = linkRepository.findById(batch.getLinkId()).orElse(null);
      if (link == null) continue;
      switch (c.getPostEndAction()) {
        case KEEP:
          break;
        case EXPIRE:
          link.applyCampaignExpiration(at, null, c.getPostEndMessage());
          break;
        case REDIRECT:
          link.applyCampaignExpiration(at, c.getPostEndDestinationUrl(), null);
          break;
      }
    }
  }
}
