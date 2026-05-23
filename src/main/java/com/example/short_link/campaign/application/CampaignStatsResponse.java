package com.example.short_link.campaign.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CampaignStatsResponse(
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
      String shortCode,
      long clicks) {}

  /**
   * 그루핑 비교 — 어느 배포자/지역이 잘했는지. clickRatePerHundred 는 100장당 클릭 비율 (배포 효율). 단순 클릭 수만 보면 quantity 차이가 큰
   * batch 묶음 끼리 비교 안 됨.
   */
  public record GroupStats(
      String key, long clicks, int totalQuantity, double clickRatePerHundred) {}

  /** 시간대별 (0–23) 클릭 분포. */
  public record HourBucket(int hour, long clicks) {}

  /** 일별 클릭 추이 (campaign.startsAt ~ 현재). */
  public record DayBucket(LocalDate day, long clicks) {}

  /** Heatmap cell — DAYOFWEEK 는 1(일)~7(토), hour 는 0~23. */
  public record HeatmapCell(int dayOfWeek, int hour, long clicks) {}
}
