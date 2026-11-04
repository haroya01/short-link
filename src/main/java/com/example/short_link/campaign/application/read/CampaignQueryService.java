package com.example.short_link.campaign.application.read;

import com.example.short_link.campaign.application.dto.CampaignSummary;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryService {

  private final CampaignRepository repository;
  private final CampaignBatchRepository batchRepository;
  private final ClickTotalsReader clickTotals;

  public List<CampaignSummary> summaries(Long ownerId) {
    List<CampaignEntity> campaigns = repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    if (campaigns.isEmpty()) return List.of();
    Map<Long, Instant> startsAt = new HashMap<>();
    campaigns.forEach(c -> startsAt.put(c.getId(), c.getStartsAt()));
    List<CampaignBatchEntity> batches = batchRepository.findByCampaignIdIn(startsAt.keySet());
    Map<Long, Instant> sinceByLink = new HashMap<>();
    batches.forEach(b -> sinceByLink.put(b.getLinkId(), startsAt.get(b.getCampaignId())));
    Map<Long, Long> clicksByLink = new HashMap<>();
    clickTotals
        .countsByLinkIdsEachSince(sinceByLink)
        .forEach(row -> clicksByLink.put(row.getLinkId(), row.getCount()));

    Map<Long, long[]> totals = new HashMap<>();
    for (CampaignBatchEntity b : batches) {
      long[] sums = totals.computeIfAbsent(b.getCampaignId(), id -> new long[3]);
      sums[0]++;
      sums[1] += b.getQuantity();
      sums[2] += clicksByLink.getOrDefault(b.getLinkId(), 0L);
    }
    return campaigns.stream()
        .map(
            c -> {
              long[] sums = totals.getOrDefault(c.getId(), new long[3]);
              return new CampaignSummary(c, sums[0], sums[1], sums[2]);
            })
        .toList();
  }

  public CampaignEntity detail(Long id, Long ownerId) {
    CampaignEntity c =
        repository
            .findById(id)
            .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    if (!c.isOwnedBy(ownerId)) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
    }
    return c;
  }

  public long batchCount(Long campaignId) {
    return batchRepository.countByCampaignId(campaignId);
  }
}
