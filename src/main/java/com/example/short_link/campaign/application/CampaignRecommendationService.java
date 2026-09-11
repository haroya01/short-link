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

    List<WeightedBatch> weighted = batches.stream().map(b -> weigh(b, avgRate)).toList();
    List<Allocation> allocations = allocate(weighted, totalQuantity);
    correctRounding(allocations, totalQuantity);
    List<BatchRecommendation> recs = allocations.stream().map(Allocation::toView).toList();

    return new CampaignRecommendationView(false, null, totalQuantity, totalClicks, avgRate, recs);
  }

  private static WeightedBatch weigh(BatchStats batch, double avgRate) {
    double rate = batch.quantity() > 0 ? (batch.clicks() * 100.0) / batch.quantity() : 0.0;
    double ratio = avgRate > 0 ? rate / avgRate : 0.0;
    if (ratio < PRUNE_THRESHOLD) {
      return new WeightedBatch(batch, rate, 0, RecommendationVerdict.PRUNE);
    }
    RecommendationVerdict verdict;
    if (ratio >= 1.2) verdict = RecommendationVerdict.BOOST;
    else if (ratio >= 0.8) verdict = RecommendationVerdict.KEEP;
    else verdict = RecommendationVerdict.REDUCE;
    return new WeightedBatch(batch, rate, batch.quantity() * Math.min(ratio, MAX_BOOST), verdict);
  }

  private static List<Allocation> allocate(List<WeightedBatch> batches, int totalQuantity) {
    double rawSum = 0;
    for (WeightedBatch batch : batches) rawSum += batch.rawQuantity();
    double scale = rawSum > 0 ? totalQuantity / rawSum : 0.0;
    List<Allocation> allocations = new ArrayList<>(batches.size());
    for (WeightedBatch batch : batches) {
      int quantity =
          batch.rawQuantity() == 0
              ? 0
              : Math.max((int) Math.round(batch.rawQuantity() * scale), MIN_QUANTITY);
      allocations.add(new Allocation(batch, quantity));
    }
    return allocations;
  }

  /** 반올림과 최소 수량 적용으로 생긴 차이는 최대 배분 묶음 하나에서 보정한다. 동률이면 첫 묶음이다. */
  private static void correctRounding(List<Allocation> allocations, int totalQuantity) {
    int allocated = 0;
    int largestIndex = -1;
    int largestQuantity = -1;
    for (int i = 0; i < allocations.size(); i++) {
      int quantity = allocations.get(i).quantity();
      allocated += quantity;
      if (quantity > largestQuantity) {
        largestQuantity = quantity;
        largestIndex = i;
      }
    }
    int difference = totalQuantity - allocated;
    if (difference != 0 && largestIndex >= 0) {
      Allocation largest = allocations.get(largestIndex);
      allocations.set(
          largestIndex,
          new Allocation(largest.batch(), Math.max(0, largest.quantity() + difference)));
    }
  }

  private record WeightedBatch(
      BatchStats stats, double rate, double rawQuantity, RecommendationVerdict verdict) {}

  private record Allocation(WeightedBatch batch, int quantity) {

    BatchRecommendation toView() {
      BatchStats stats = batch.stats();
      return new BatchRecommendation(
          stats.batchId(),
          stats.batchName(),
          stats.distributor(),
          stats.area(),
          stats.quantity(),
          stats.clicks(),
          batch.rate(),
          quantity,
          quantity - stats.quantity(),
          batch.verdict());
    }
  }
}
