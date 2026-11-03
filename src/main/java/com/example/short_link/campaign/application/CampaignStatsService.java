package com.example.short_link.campaign.application;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.dto.CampaignStatsCompareView;
import com.example.short_link.campaign.application.dto.CampaignStatsView;
import com.example.short_link.campaign.application.dto.CampaignStatsView.BatchStats;
import com.example.short_link.campaign.application.dto.CampaignStatsView.DayBucket;
import com.example.short_link.campaign.application.dto.CampaignStatsView.GroupStats;
import com.example.short_link.campaign.application.dto.CampaignStatsView.HeatmapCell;
import com.example.short_link.campaign.application.dto.CampaignStatsView.HourBucket;
import com.example.short_link.campaign.application.dto.CampaignStatsView.TimeBuckets;
import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.common.time.OffsetPeriod;
import com.example.short_link.common.time.ReportZones;
import com.example.short_link.link.stats.application.read.ClickTimeReader;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DailyClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignStatsService {

  private final CampaignQueryService campaignQuery;
  private final CampaignBatchService batchService;
  private final ClickTotalsReader clickTotals;
  private final ClickTimeReader clickTime;
  private final UserAccessLookup users;
  private final Clock clock;

  @Transactional(readOnly = true)
  public CampaignStatsView statsFor(Long campaignId, Long ownerId) {
    CampaignEntity campaign = campaignQuery.detail(campaignId, ownerId);
    List<BatchWithLink> batches = batchService.list(campaignId, ownerId);
    ZoneId zone = ownerZone(campaign.getOwnerId());

    if (batches.isEmpty()) {
      return new CampaignStatsView(
          0L,
          0L,
          null,
          List.of(),
          List.of(),
          List.of(),
          new TimeBuckets(List.of(), List.of(), List.of(), zone.getId()));
    }

    List<Long> linkIds = batches.stream().map(b -> b.link().linkId().value()).toList();

    Map<Long, Long> clicksByLink =
        toMap(clickTotals.countsByLinkIdsSince(linkIds, campaign.getStartsAt()));
    Map<Long, Long> testByLink =
        toMap(clickTotals.countsByLinkIdsBefore(linkIds, campaign.getStartsAt()));

    List<BatchStats> byBatch = new ArrayList<>(batches.size());
    long totalClicks = 0;
    for (BatchWithLink bwl : batches) {
      long clicks = clicksByLink.getOrDefault(bwl.link().linkId().value(), 0L);
      totalClicks += clicks;
      byBatch.add(
          new BatchStats(
              bwl.batch().getId(),
              bwl.batch().getName(),
              bwl.batch().getDistributorName(),
              bwl.batch().getAreaLabel(),
              bwl.batch().getQuantity(),
              bwl.link().shortCode(),
              clicks));
    }

    long testScans = testByLink.values().stream().mapToLong(Long::longValue).sum();

    List<GroupStats> byDistributor = groupBy(byBatch, BatchStats::distributor);
    List<GroupStats> byArea = groupBy(byBatch, BatchStats::area);

    Instant until = clock.instant().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
    List<OffsetPeriod> periods = OffsetPeriod.between(zone, campaign.getStartsAt(), until);
    List<HourBucket> byHour =
        sum(
                periods,
                p -> clickTime.findHourlyClicksByLinkIds(linkIds, p.from(), p.until(), p.offset()),
                HourClickRow::getHour,
                HourClickRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new HourBucket(e.getKey(), e.getValue()))
            .toList();
    List<DayBucket> byDay =
        sum(
                periods,
                p -> clickTime.findDailyClicksByLinkIds(linkIds, p.from(), p.until(), p.offset()),
                DailyClickRow::getDay,
                DailyClickRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new DayBucket(e.getKey(), e.getValue()))
            .toList();
    List<HeatmapCell> heatmap =
        sum(
                periods,
                p -> clickTime.findHeatmapByLinkIds(linkIds, p.from(), p.until(), p.offset()),
                r -> new DowHour(r.getDow(), r.getHour()),
                HeatmapRow::getCount)
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey(DowHour.ORDER))
            .map(e -> new HeatmapCell(e.getKey().dow(), e.getKey().hour(), e.getValue()))
            .toList();

    return new CampaignStatsView(
        totalClicks,
        testScans,
        testScans > 0
            ? clickTotals.findLastClickBeforeByLinkIds(linkIds, campaign.getStartsAt())
            : null,
        byBatch,
        byDistributor,
        byArea,
        new TimeBuckets(byHour, byDay, heatmap, zone.getId()));
  }

  @Transactional(readOnly = true)
  public CampaignStatsCompareView compare(List<Long> campaignIds, Long ownerId) {
    List<CampaignStatsCompareView.CampaignWithStats> result = new ArrayList<>(campaignIds.size());
    for (Long id : campaignIds) {
      CampaignEntity c = campaignQuery.detail(id, ownerId);
      CampaignStatsView stats = statsFor(id, ownerId);
      result.add(new CampaignStatsCompareView.CampaignWithStats(id, c.getName(), stats));
    }
    return new CampaignStatsCompareView(result);
  }

  private ZoneId ownerZone(Long ownerId) {
    return users.timezone(ownerId).map(ReportZones::zoneOf).orElse(ReportZones.DEFAULT_ZONE);
  }

  private static <R, K> Map<K, Long> sum(
      List<OffsetPeriod> periods,
      Function<OffsetPeriod, List<R>> query,
      Function<R, K> key,
      Function<R, Long> count) {
    Map<K, Long> sums = new HashMap<>();
    for (OffsetPeriod period : periods) {
      for (R row : query.apply(period)) {
        sums.merge(key.apply(row), count.apply(row), Long::sum);
      }
    }
    return sums;
  }

  private record DowHour(int dow, int hour) {
    static final Comparator<DowHour> ORDER =
        Comparator.comparingInt(DowHour::dow).thenComparingInt(DowHour::hour);
  }

  private static Map<Long, Long> toMap(List<LinkClickCount> rows) {
    Map<Long, Long> out = new HashMap<>();
    for (LinkClickCount row : rows) {
      out.put(row.getLinkId(), row.getCount());
    }
    return out;
  }

  private static List<GroupStats> groupBy(
      List<BatchStats> batches, Function<BatchStats, String> keyFn) {
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
