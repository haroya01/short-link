package com.example.short_link.campaign.application;

import com.example.short_link.campaign.application.dto.CampaignStatsCompareResponse;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse.BatchStats;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse.DayBucket;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse.GroupStats;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse.HeatmapCell;
import com.example.short_link.campaign.application.dto.CampaignStatsResponse.HourBucket;
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

  // 시간대/일별/heatmap aggregation 의 viewer 기준 timezone. 캠페인 도메인에 timezone 필드가
  // 따로 없어서 한국 서비스 default 로 고정. 후속 PR 에서 campaign 또는 user 의 timezone 필드 추가
  // 시 여기에 주입하면 됨.
  private static final String DEFAULT_TIMEZONE = "Asia/Seoul";

  private final com.example.short_link.campaign.application.read.CampaignQueryService campaignQuery;
  private final CampaignBatchService batchService;
  private final ClickEventRepository clickEventRepository;

  @Transactional(readOnly = true)
  public CampaignStatsResponse statsFor(Long campaignId, Long ownerId) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    List<BatchWithLink> batches = batchService.list(campaignId, ownerId);

    if (batches.isEmpty()) {
      return new CampaignStatsResponse(
          0L, 0L, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
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

    List<HourBucket> byHour =
        clickEventRepository
            .findHourlyClicksByLinkIdsSince(linkIds, campaign.getStartsAt(), DEFAULT_TIMEZONE)
            .stream()
            .map(r -> new HourBucket(r.getHour(), r.getCount()))
            .toList();
    List<DayBucket> byDay =
        clickEventRepository
            .findDailyClicksByLinkIdsSince(linkIds, campaign.getStartsAt(), DEFAULT_TIMEZONE)
            .stream()
            .map(r -> new DayBucket(r.getDay(), r.getCount()))
            .toList();
    List<HeatmapCell> heatmap =
        clickEventRepository
            .findHeatmapByLinkIdsSince(linkIds, campaign.getStartsAt(), DEFAULT_TIMEZONE)
            .stream()
            .map(r -> new HeatmapCell(r.getDow(), r.getHour(), r.getCount()))
            .toList();

    return new CampaignStatsResponse(
        totalClicks,
        testScans,
        testScans > 0
            ? clickEventRepository.findLastClickBeforeByLinkIds(linkIds, campaign.getStartsAt())
            : null,
        byBatch,
        byDistributor,
        byArea,
        byHour,
        byDay,
        heatmap);
  }

  /**
   * 두 캠페인의 stats 를 side-by-side 로 반환. UI 에서 1차 → 2차 비교 또는 같은 사장의 두 캠페인 효율 비교에 사용. 각 캠페인은 owner 검증을
   * 위해 detail() 을 통과시킨다.
   */
  @Transactional(readOnly = true)
  public CampaignStatsCompareResponse compare(List<Long> campaignIds, Long ownerId) {
    List<CampaignStatsCompareResponse.CampaignWithStats> result =
        new ArrayList<>(campaignIds.size());
    for (Long id : campaignIds) {
      CampaignEntity c = campaignQuery.detail(id, ownerId);
      CampaignStatsResponse stats = statsFor(id, ownerId);
      result.add(new CampaignStatsCompareResponse.CampaignWithStats(id, c.getName(), stats));
    }
    return new CampaignStatsCompareResponse(result);
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
