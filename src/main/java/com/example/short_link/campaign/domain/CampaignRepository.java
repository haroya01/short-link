package com.example.short_link.campaign.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

  List<CampaignEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

  /** 자동 ACTIVE/ENDED 전환 스케줄러용 — 시작 도달한 DRAFT, 종료 도달한 ACTIVE 를 한 번에 훑는다. */
  List<CampaignEntity> findByStatusAndEndsAtLessThanEqual(CampaignStatus status, Instant cutoff);

  List<CampaignEntity> findByStatusAndStartsAtLessThanEqual(CampaignStatus status, Instant cutoff);
}
