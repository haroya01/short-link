package com.example.short_link.campaign.application;

import com.example.short_link.campaign.application.dto.CampaignRecommendationView;
import com.example.short_link.campaign.application.dto.CampaignRecommendationView.BatchRecommendation;
import com.example.short_link.campaign.application.dto.CampaignRecommendationView.RecommendationVerdict;
import com.example.short_link.campaign.application.dto.CampaignStatsView;
import com.example.short_link.campaign.application.dto.CampaignStatsView.BatchStats;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다음 배포 시 각 batch 의 quantity 를 어떻게 조정할지 추천.
 *
 * <p>알고리즘 — Proportional reallocation + threshold-based prune:
 *
 * <ol>
 *   <li>평균 100장당 클릭 (avgRate) 계산
 *   <li>각 batch 의 ratePerHundred / avgRate 비율 (ratio) 계산
 *   <li>ratio < PRUNE_THRESHOLD (0.3) → 폐기 (recommended = 0)
 *   <li>그 외 → quantity × min(ratio, MAX_BOOST(3.0)) 로 raw 산출
 *   <li>총 quantity 유지하도록 normalize (sum(raw) → totalQuantity)
 *   <li>0 < final < MIN_QUANTITY (50) → MIN_QUANTITY 로 올림 (운영 효율 — 너무 작은 batch 비효율)
 * </ol>
 *
 * <p>Insufficient guards — 총 클릭 < 10 또는 batch 수 < 2 면 추천 안 함 (insufficient = true). 데이터 부족 시 추천이
 * statistical noise 라 사용자 손해.
 */
@Service
@RequiredArgsConstructor
public class CampaignRecommendationService {

  /** 평균의 X% 미만 batch 는 다음 배포에서 폐기. */
  private static final double PRUNE_THRESHOLD = 0.3;

  /** 한 batch 가 최대 3배까지 증가 가능 (특정 batch 가 전체를 독점하지 못하게). */
  private static final double MAX_BOOST = 3.0;

  /** Non-zero batch 의 최소 quantity — 운영 비효율 회피. */
  private static final int MIN_QUANTITY = 50;

  /** 추천 신뢰 임계 — 총 클릭 미만이면 추천 안 함. */
  private static final int MIN_TOTAL_CLICKS = 10;

  /** 추천 신뢰 임계 — batch 1개면 재할당 의미 없음. */
  private static final int MIN_BATCH_COUNT = 2;

  private final CampaignStatsService statsService;

  @Transactional(readOnly = true)
  public CampaignRecommendationView recommend(Long campaignId, Long ownerId) {
    CampaignStatsView stats = statsService.statsFor(campaignId, ownerId);
    List<BatchStats> batches = stats.byBatch();

    int totalQuantity = batches.stream().mapToInt(BatchStats::quantity).sum();
    int totalClicks = (int) batches.stream().mapToLong(BatchStats::clicks).sum();

    if (batches.size() < MIN_BATCH_COUNT) {
      return new CampaignRecommendationView(
          true,
          "재할당 대상이 부족합니다 — 배포 묶음을 2개 이상 만든 뒤 다시 확인하세요.",
          totalQuantity,
          totalClicks,
          0.0,
          List.of());
    }
    if (totalClicks < MIN_TOTAL_CLICKS) {
      return new CampaignRecommendationView(
          true,
          "데이터가 적습니다 — 총 클릭 10회 이상 누적되면 추천을 보여드립니다.",
          totalQuantity,
          totalClicks,
          0.0,
          List.of());
    }

    double avgRate = totalQuantity > 0 ? (totalClicks * 100.0) / totalQuantity : 0.0;

    // Step 1: 각 batch 의 raw quantity 계산
    double[] raw = new double[batches.size()];
    RecommendationVerdict[] verdicts = new RecommendationVerdict[batches.size()];
    for (int i = 0; i < batches.size(); i++) {
      BatchStats b = batches.get(i);
      double rate = b.quantity() > 0 ? (b.clicks() * 100.0) / b.quantity() : 0.0;
      double ratio = avgRate > 0 ? rate / avgRate : 0.0;

      if (ratio < PRUNE_THRESHOLD) {
        raw[i] = 0;
        verdicts[i] = RecommendationVerdict.PRUNE;
      } else {
        double boost = Math.min(ratio, MAX_BOOST);
        raw[i] = b.quantity() * boost;
        if (ratio >= 1.2) verdicts[i] = RecommendationVerdict.BOOST;
        else if (ratio >= 0.8) verdicts[i] = RecommendationVerdict.KEEP;
        else verdicts[i] = RecommendationVerdict.REDUCE;
      }
    }

    // Step 2: 총 quantity 유지하도록 normalize
    double rawSum = 0;
    for (double r : raw) rawSum += r;
    double scale = rawSum > 0 ? totalQuantity / rawSum : 0.0;

    int[] finalQty = new int[batches.size()];
    for (int i = 0; i < batches.size(); i++) {
      if (raw[i] == 0) {
        finalQty[i] = 0;
      } else {
        int scaled = (int) Math.round(raw[i] * scale);
        finalQty[i] = Math.max(scaled, MIN_QUANTITY);
      }
    }

    // Step 3: rounding 으로 sum 이 totalQuantity 와 살짝 어긋날 수 있음 — 가장 큰 boost batch 에서 보정.
    int sumFinal = 0;
    for (int q : finalQty) sumFinal += q;
    int diff = totalQuantity - sumFinal;
    if (diff != 0) {
      int maxIdx = -1;
      int maxQty = -1;
      for (int i = 0; i < finalQty.length; i++) {
        if (finalQty[i] > maxQty) {
          maxQty = finalQty[i];
          maxIdx = i;
        }
      }
      if (maxIdx >= 0) {
        finalQty[maxIdx] = Math.max(0, finalQty[maxIdx] + diff);
      }
    }

    // Step 4: BatchRecommendation list 만듦
    List<BatchRecommendation> recs = new ArrayList<>(batches.size());
    for (int i = 0; i < batches.size(); i++) {
      BatchStats b = batches.get(i);
      double rate = b.quantity() > 0 ? (b.clicks() * 100.0) / b.quantity() : 0.0;
      recs.add(
          new BatchRecommendation(
              b.batchId(),
              b.batchName(),
              b.distributor(),
              b.area(),
              b.quantity(),
              b.clicks(),
              rate,
              finalQty[i],
              finalQty[i] - b.quantity(),
              verdicts[i]));
    }

    return new CampaignRecommendationView(false, null, totalQuantity, totalClicks, avgRate, recs);
  }
}
