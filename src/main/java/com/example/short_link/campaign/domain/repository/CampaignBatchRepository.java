package com.example.short_link.campaign.domain.repository;

import com.example.short_link.campaign.domain.*;
import java.util.List;
import java.util.Optional;

public interface CampaignBatchRepository {

  Optional<CampaignBatchEntity> findById(Long id);

  CampaignBatchEntity save(CampaignBatchEntity batch);

  void delete(CampaignBatchEntity batch);

  List<CampaignBatchEntity> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

  long countByCampaignId(Long campaignId);
}
