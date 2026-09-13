package com.example.short_link.campaign.application.dto;

import java.util.List;

/** {@code insufficient}이면 추천 목록은 비어 있으며 {@code insufficientReason}에 사유를 담는다. */
public record CampaignRecommendationView(
    boolean insufficient,
    String insufficientReason,
    int totalQuantity,
    int totalClicks,
    double avgRatePerHundred,
    List<BatchRecommendation> recommendations) {

  public record BatchRecommendation(
      Long batchId,
      String batchName,
      String distributor,
      String area,
      int currentQuantity,
      long currentClicks,
      double currentRatePerHundred,
      int recommendedQuantity,
      int delta,
      RecommendationVerdict verdict) {}

  public enum RecommendationVerdict {
    BOOST,
    KEEP,
    REDUCE,
    PRUNE
  }
}
