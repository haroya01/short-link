package com.example.short_link.campaign.application;

import com.example.short_link.campaign.api.CampaignStatsResponse;
import com.example.short_link.campaign.api.CampaignStatsResponse.BatchStats;
import com.example.short_link.campaign.api.CampaignStatsResponse.GroupStats;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.ClickEventRepository.LinkClickCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignStatsService {

  private final CampaignService campaignService;
  private final CampaignBatchService batchService;
  private final ClickEventRepository clickEventRepository;

  @Transactional(readOnly = true)
  public CampaignStatsResponse statsFor(Long campaignId, Long ownerId) {
    CampaignEntity campaign = campaignService.detail(campaignId, ownerId);
    List<BatchWithLink> batches = batchService.list(campaignId, ownerId);

    if (batches.isEmpty()) {
      return new CampaignStatsResponse(0L, 0L, null, List.of(), List.of(), List.of());
    }

    List<Long> linkIds = batches.stream().map(b -> b.link().getId()).toList();

    Map<Long, Long> clicksByLink =
        toMap(clickEventRepository.countsByLinkIdsSince(linkIds, campaign.getStartsAt()));
    Map<Long, Long> testByLink =
        toMap(clickEventRepository.countsByLinkIdsBefore(linkIds, campaign.getStartsAt()));

    List<BatchStats> byBatch = new ArrayList<>(batches.size());
    long totalClicks = 0;
    for (BatchWithLink bwl : batches) {
      long clicks = clicksByLink.getOrDefault(bwl.link().getId(), 0L);
      totalClicks += clicks;
      byBatch.add(
          new BatchStats(
              bwl.batch().getId(),
              bwl.batch().getName(),
              bwl.batch().getDistributorName(),
              bwl.batch().getAreaLabel(),
              bwl.batch().getQuantity(),
              bwl.link().getShortCode(),
              clicks));
    }

    long testScans = testByLink.values().stream().mapToLong(Long::longValue).sum();

    List<GroupStats> byDistributor = groupBy(byBatch, BatchStats::distributor);
    List<GroupStats> byArea = groupBy(byBatch, BatchStats::area);

    return new CampaignStatsResponse(
        totalClicks,
        testScans,
        testScans > 0
            ? clickEventRepository.findLastClickBeforeByLinkIds(linkIds, campaign.getStartsAt())
            : null,
        byBatch,
        byDistributor,
        byArea);
  }

  private static Map<Long, Long> toMap(List<LinkClickCount> rows) {
    Map<Long, Long> out = new HashMap<>();
    for (LinkClickCount row : rows) {
      out.put(row.getLinkId(), row.getCount());
    }
    return out;
  }

  private static List<GroupStats> groupBy(
      List<BatchStats> batches, java.util.function.Function<BatchStats, String> keyFn) {
    Map<String, long[]> agg = new HashMap<>();
    for (BatchStats b : batches) {
      String key = keyFn.apply(b);
      if (key == null || key.isBlank()) {
        continue;
      }
      long[] sums = agg.computeIfAbsent(key, k -> new long[2]);
      sums[0] += b.clicks();
      sums[1] += b.quantity();
    }
    List<GroupStats> result = new ArrayList<>(agg.size());
    for (Map.Entry<String, long[]> e : agg.entrySet()) {
      long clicks = e.getValue()[0];
      long quantity = e.getValue()[1];
      double ratePerHundred = quantity > 0 ? (clicks * 100.0) / quantity : 0.0;
      result.add(new GroupStats(e.getKey(), clicks, (int) quantity, ratePerHundred));
    }
    result.sort(Comparator.comparingLong(GroupStats::clicks).reversed());
    return result;
  }
}
