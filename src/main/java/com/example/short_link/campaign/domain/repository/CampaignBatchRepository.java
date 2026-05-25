package com.example.short_link.campaign.domain.repository;

import com.example.short_link.campaign.domain.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignBatchRepository extends JpaRepository<CampaignBatchEntity, Long> {

  List<CampaignBatchEntity> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

  long countByCampaignId(Long campaignId);
}
