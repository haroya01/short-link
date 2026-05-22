package com.example.short_link.campaign.api;

import java.util.List;

/**
 * 다음 배포 시 각 batch 의 quantity 를 어떻게 조정할지 추천.
 *
 * <p>알고리즘: ratePerHundred 기반 proportional reallocation + 평균 30% 미만 batch 폐기. 총 quantity 유지. 자세한 동작은
 * {@link com.example.short_link.campaign.application.CampaignRecommendationService} 참고.
 *
 * <p>insufficient 가 true 이면 추천 생성 안 됨 (recommendations 빈 배열). 클라이언트는 reason 으로 사용자에게 메시지 표시.
 */
public record CampaignRecommendationResponse(
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
    /** 평균보다 효율 우수 — quantity 증가 (delta > 0). */
    BOOST,
    /** 평균 부근 — quantity 유지 또는 약간 조정. */
    KEEP,
    /** 평균보다 낮음 — quantity 감소 (delta < 0). */
    REDUCE,
    /** 평균의 30% 미만 — 다음 배포에서 폐기 권장 (recommendedQuantity = 0). */
    PRUNE
  }
}
