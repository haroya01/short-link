package com.example.short_link.campaign.api;

import java.time.Instant;
import java.util.List;

public record CampaignStatsResponse(
    long totalClicks,
    long testScans,
    Instant lastTestScanAt,
    List<BatchStats> byBatch,
    List<GroupStats> byDistributor,
    List<GroupStats> byArea) {

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
}
