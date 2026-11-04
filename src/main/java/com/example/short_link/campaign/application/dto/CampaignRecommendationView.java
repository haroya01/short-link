package com.example.short_link.campaign.application.dto;

import java.util.List;

public record CampaignRecommendationView(
    boolean insufficient,
    String insufficientReason,
    InsufficientCode insufficientCode,
    Integer insufficientMinimum,
    Integer insufficientCurrent,
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

  public enum InsufficientCode {
    TOO_FEW_BATCHES,
    TOO_FEW_CLICKS
  }

  public enum RecommendationVerdict {
    BOOST,
    KEEP,
    REDUCE,
    PRUNE
  }
}
