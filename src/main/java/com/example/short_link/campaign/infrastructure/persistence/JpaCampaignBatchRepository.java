package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCampaignBatchRepository
    extends JpaRepository<CampaignBatchEntity, Long>, CampaignBatchRepository {

  boolean existsByLinkId(Long linkId);
}
