package com.example.short_link.campaign.application.read;

import com.example.short_link.campaign.application.CampaignNotFoundException;
import com.example.short_link.campaign.application.CampaignNotOwnedException;
import com.example.short_link.campaign.domain.CampaignBatchRepository;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryService {

  private final CampaignRepository repository;
  private final CampaignBatchRepository batchRepository;

  public List<CampaignEntity> list(Long ownerId) {
    return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
  }

  public CampaignEntity detail(Long id, Long ownerId) {
    CampaignEntity c = repository.findById(id).orElseThrow(CampaignNotFoundException::new);
    if (!c.isOwnedBy(ownerId)) {
      throw new CampaignNotOwnedException();
    }
    return c;
  }

  public long batchCount(Long campaignId) {
    return batchRepository.countByCampaignId(campaignId);
  }
}
