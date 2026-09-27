package com.example.short_link.campaign.application.dto;

import java.util.List;

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
