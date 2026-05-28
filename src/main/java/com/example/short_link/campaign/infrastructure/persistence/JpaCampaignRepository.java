package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCampaignRepository extends JpaRepository<CampaignEntity, Long> {

  List<CampaignEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

  List<CampaignEntity> findByStatusAndEndsAtLessThanEqual(CampaignStatus status, Instant cutoff);

  List<CampaignEntity> findByStatusAndStartsAtLessThanEqual(CampaignStatus status, Instant cutoff);
}
