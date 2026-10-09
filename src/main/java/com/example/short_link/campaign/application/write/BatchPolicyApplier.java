package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BatchPolicyApplier {

  private final CampaignBatchRepository batchRepository;
  private final LinkRepository linkRepository;
  private final LinkExpirationPolicyRepository expirationPolicyRepository;
  private final LinkCacheEviction linkCacheEviction;

  void apply(CampaignEntity c, Instant at) {
    List<CampaignBatchEntity> batches =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(c.getId());
    List<ShortCode> applied = new ArrayList<>();
    for (CampaignBatchEntity batch : batches) {
      LinkEntity link = linkRepository.findById(batch.getLinkId()).orElse(null);
      if (link == null) continue;
      switch (c.getPostEndAction()) {
        case KEEP:
          break;
        case EXPIRE:
          link.applyCampaignExpiration(at, null, c.getPostEndMessage());
          mirrorPolicy(link);
          applied.add(link.getShortCode());
          break;
        case REDIRECT:
          link.applyCampaignExpiration(at, c.getPostEndDestinationUrl(), null);
          mirrorPolicy(link);
          applied.add(link.getShortCode());
          break;
      }
    }
    linkCacheEviction.evictAllAfterCommit(applied);
  }

  private void mirrorPolicy(LinkEntity link) {
    LinkExpirationPolicyEntity policy =
        expirationPolicyRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkExpirationPolicyEntity(link.linkId()));
    policy.changeExpiredMessage(link.getExpiredMessage());
    policy.changeExpiredRedirectUrl(link.getExpiredRedirectUrl());
    expirationPolicyRepository.save(policy);
  }
}
