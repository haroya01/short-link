package com.example.short_link.campaign.domain.repository;

import com.example.short_link.campaign.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {

  Optional<CampaignEntity> findById(Long id);

  CampaignEntity save(CampaignEntity campaign);

  List<CampaignEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

  List<CampaignEntity> findByStatusAndEndsAtLessThanEqual(CampaignStatus status, Instant cutoff);

  List<CampaignEntity> findByStatusAndStartsAtLessThanEqual(CampaignStatus status, Instant cutoff);
}
