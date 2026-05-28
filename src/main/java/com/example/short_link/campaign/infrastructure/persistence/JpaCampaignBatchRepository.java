package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCampaignBatchRepository extends JpaRepository<CampaignBatchEntity, Long> {

  List<CampaignBatchEntity> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

  long countByCampaignId(Long campaignId);
}
