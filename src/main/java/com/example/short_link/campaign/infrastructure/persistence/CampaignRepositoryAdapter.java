package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CampaignRepositoryAdapter implements CampaignRepository {

  private final JpaCampaignRepository jpa;

  @Override
  public Optional<CampaignEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public CampaignEntity save(CampaignEntity campaign) {
    return jpa.save(campaign);
  }

  @Override
  public List<CampaignEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId) {
    return jpa.findByOwnerIdOrderByCreatedAtDesc(ownerId);
  }

  @Override
  public List<CampaignEntity> findByStatusAndEndsAtLessThanEqual(
      CampaignStatus status, Instant cutoff) {
    return jpa.findByStatusAndEndsAtLessThanEqual(status, cutoff);
  }

  @Override
  public List<CampaignEntity> findByStatusAndStartsAtLessThanEqual(
      CampaignStatus status, Instant cutoff) {
    return jpa.findByStatusAndStartsAtLessThanEqual(status, cutoff);
  }
}
