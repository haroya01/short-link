package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CampaignBatchRepositoryAdapter implements CampaignBatchRepository {

  private final JpaCampaignBatchRepository jpa;

  @Override
  public Optional<CampaignBatchEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public CampaignBatchEntity save(CampaignBatchEntity batch) {
    return jpa.save(batch);
  }

  @Override
  public void delete(CampaignBatchEntity batch) {
    jpa.delete(batch);
  }

  @Override
  public List<CampaignBatchEntity> findByCampaignIdOrderByCreatedAtAsc(Long campaignId) {
    return jpa.findByCampaignIdOrderByCreatedAtAsc(campaignId);
  }

  @Override
  public long countByCampaignId(Long campaignId) {
    return jpa.countByCampaignId(campaignId);
  }
}
