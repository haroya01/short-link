package com.example.short_link.campaign.application.dto;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CampaignStatsView(
    long totalClicks,
    long testScans,
    Instant lastTestScanAt,
    List<BatchStats> byBatch,
    List<GroupStats> byDistributor,
    List<GroupStats> byArea,
    List<HourBucket> byHour,
    List<DayBucket> byDay,
    List<HeatmapCell> heatmap) {

  public record BatchStats(
      Long batchId,
      String batchName,
      String distributor,
      String area,
      int quantity,
      ShortCode shortCode,
      long clicks) {}

  /** 배포 수량이 다른 묶음을 비교하기 위해 클릭 수를 100장 기준으로 환산한다. */
  public record GroupStats(
      String key, long clicks, int totalQuantity, double clickRatePerHundred) {}

  /** 시간대별 (0–23) 클릭 분포. */
  public record HourBucket(int hour, long clicks) {}

  /** 일별 클릭 추이 (campaign.startsAt ~ 현재). */
  public record DayBucket(LocalDate day, long clicks) {}

  /** Heatmap cell — DAYOFWEEK 는 1(일)~7(토), hour 는 0~23. */
  public record HeatmapCell(int dayOfWeek, int hour, long clicks) {}
}
