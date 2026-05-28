package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.dto.CampaignRecommendationView;
import com.example.short_link.campaign.application.dto.CampaignRecommendationView.RecommendationVerdict;
import com.example.short_link.campaign.application.dto.CampaignStatsView;
import com.example.short_link.campaign.application.dto.CampaignStatsView.BatchStats;
import com.example.short_link.link.domain.ShortCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CampaignRecommendationServiceTest {

  private final CampaignStatsService statsService = mock(CampaignStatsService.class);
  private final CampaignRecommendationService service =
      new CampaignRecommendationService(statsService);

  private static BatchStats batch(long id, int qty, long clicks) {
    return new BatchStats(
        id, "batch-" + id, "dist", "area", qty, new ShortCode("abc" + id), clicks);
  }

  private CampaignRecommendationView recommendWith(BatchStats... batches) {
    when(statsService.statsFor(1L, 9L))
        .thenReturn(
            new CampaignStatsView(
                0,
                0,
                null,
                List.of(batches),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    return service.recommend(1L, 9L);
  }

  @Test
  void singleBatchYieldsInsufficient() {
    CampaignRecommendationView view = recommendWith(batch(1, 100, 5));

    assertThat(view.insufficient()).isTrue();
    assertThat(view.recommendations()).isEmpty();
    assertThat(view.insufficientReason()).contains("재할당");
    assertThat(view.totalQuantity()).isEqualTo(100);
    assertThat(view.totalClicks()).isEqualTo(5);
  }

  @Test
  void belowMinTotalClicksIsInsufficient() {
    CampaignRecommendationView view = recommendWith(batch(1, 100, 2), batch(2, 100, 3));

    assertThat(view.insufficient()).isTrue();
    assertThat(view.insufficientReason()).contains("데이터");
    assertThat(view.recommendations()).isEmpty();
  }

  @Test
  void reallocatesQuantityProportionallyByRate() {
    CampaignRecommendationView view = recommendWith(batch(1, 100, 50), batch(2, 100, 10));

    assertThat(view.insufficient()).isFalse();
    assertThat(view.totalQuantity()).isEqualTo(200);
    assertThat(view.totalClicks()).isEqualTo(60);
    assertThat(view.avgRatePerHundred()).isEqualTo(30.0);
    assertThat(view.recommendations()).hasSize(2);
    int sum =
        view.recommendations().stream()
            .mapToInt(CampaignRecommendationView.BatchRecommendation::recommendedQuantity)
            .sum();
    assertThat(sum).isEqualTo(200);
    assertThat(view.recommendations().get(0).verdict()).isEqualTo(RecommendationVerdict.BOOST);
    assertThat(view.recommendations().get(0).delta()).isGreaterThan(0);
    assertThat(view.recommendations().get(1).delta()).isLessThan(0);
  }

  @Test
  void prunesUnderperformingBatchAndRedistributes() {
    CampaignRecommendationView view =
        recommendWith(batch(1, 100, 50), batch(2, 100, 50), batch(3, 100, 1));

    var rec3 =
        view.recommendations().stream().filter(r -> r.batchId() == 3L).findFirst().orElseThrow();
    assertThat(rec3.verdict()).isEqualTo(RecommendationVerdict.PRUNE);
    assertThat(rec3.recommendedQuantity()).isZero();

    int sum =
        view.recommendations().stream()
            .mapToInt(CampaignRecommendationView.BatchRecommendation::recommendedQuantity)
            .sum();
    assertThat(sum).isEqualTo(300);
  }

  @Test
  void boostIsCappedAtMaxBoost() {
    CampaignRecommendationView view =
        recommendWith(batch(1, 50, 100), batch(2, 50, 5), batch(3, 50, 5));

    var top =
        view.recommendations().stream().filter(r -> r.batchId() == 1L).findFirst().orElseThrow();
    assertThat(top.recommendedQuantity()).isLessThanOrEqualTo(top.currentQuantity() * 3);
    assertThat(top.verdict()).isEqualTo(RecommendationVerdict.BOOST);
  }

  @Test
  void smallNonZeroResultIsFloored() {
    CampaignRecommendationView view = recommendWith(batch(1, 10, 30), batch(2, 1000, 100));

    var small =
        view.recommendations().stream().filter(r -> r.batchId() == 1L).findFirst().orElseThrow();
    assertThat(small.recommendedQuantity()).isGreaterThanOrEqualTo(50);
  }

  @Test
  void zeroQuantityBatchIsHandledAsPrune() {
    CampaignRecommendationView view = recommendWith(batch(1, 0, 0), batch(2, 100, 25));

    var zero =
        view.recommendations().stream().filter(r -> r.batchId() == 1L).findFirst().orElseThrow();
    assertThat(zero.recommendedQuantity()).isZero();
    assertThat(zero.verdict()).isEqualTo(RecommendationVerdict.PRUNE);
  }

  @Test
  void verdictKeepWhenWithinAverageBand() {
    CampaignRecommendationView view =
        recommendWith(batch(1, 100, 20), batch(2, 100, 22), batch(3, 100, 21));

    assertThat(view.insufficient()).isFalse();
    assertThat(view.recommendations())
        .allMatch(
            r ->
                r.verdict() == RecommendationVerdict.KEEP
                    || r.verdict() == RecommendationVerdict.BOOST
                    || r.verdict() == RecommendationVerdict.REDUCE);
  }
}
