package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.api.CampaignBatchCreateRequest;
import com.example.short_link.campaign.api.CampaignCreateRequest;
import com.example.short_link.campaign.api.CampaignStatsResponse;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignStatsServiceTest {

  @Autowired private CampaignStatsService statsService;
  @Autowired private CampaignBatchService batchService;

  @Autowired
  private com.example.short_link.campaign.application.write.CreateCampaignUseCase createCampaign;

  @Autowired
  private com.example.short_link.campaign.application.write.ArchiveCampaignUseCase archiveCampaign;

  @Autowired private ClickEventRepository clickEventRepository;
  @Autowired private UserRepository userRepository;

  private Long newOwner(String suffix) {
    return userRepository
        .save(new UserEntity("u-stats-" + suffix + "@x.com", "google", suffix))
        .getId();
  }

  private void recordClick(Long linkId, Instant at, boolean bot) {
    ClickEventEntity evt = ClickEventEntity.builder().linkId(linkId).clickedAt(at).bot(bot).build();
    clickEventRepository.save(evt);
  }

  @Test
  void totalClicksOnlyCountsHumanClicksAfterStart() {
    Long owner = newOwner("total");
    Instant start = Instant.now();
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C", start, start.plusSeconds(3600), "https://example.com/d", null, null, null));
    BatchWithLink batch =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", "A", "East", 100, null, null));

    recordClick(batch.link().getId(), start.plusSeconds(60), false);
    recordClick(batch.link().getId(), start.plusSeconds(120), false);
    recordClick(batch.link().getId(), start.plusSeconds(180), true); // bot — 제외

    CampaignStatsResponse stats = statsService.statsFor(campaign.getId(), owner);

    assertThat(stats.totalClicks()).isEqualTo(2L);
    assertThat(stats.byBatch()).hasSize(1);
    assertThat(stats.byBatch().get(0).clicks()).isEqualTo(2L);
  }

  @Test
  void preStartClicksSurfacedAsTestScansNotTotal() {
    Long owner = newOwner("test");
    Instant start = Instant.now().plusSeconds(3600);
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C", start, start.plusSeconds(3600), "https://example.com/d", null, null, null));
    BatchWithLink batch =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));

    Instant testScan = start.minusSeconds(60);
    recordClick(batch.link().getId(), testScan, false);
    recordClick(batch.link().getId(), testScan.plusSeconds(30), false);

    CampaignStatsResponse stats = statsService.statsFor(campaign.getId(), owner);

    assertThat(stats.totalClicks()).isZero();
    assertThat(stats.testScans()).isEqualTo(2L);
    assertThat(stats.lastTestScanAt()).isAfterOrEqualTo(testScan);
  }

  @Test
  void distributorGroupAggregatesClicksAndQuantityWithRate() {
    Long owner = newOwner("dist");
    Instant start = Instant.now();
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C", start, start.plusSeconds(3600), "https://example.com/d", null, null, null));
    BatchWithLink a1 =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("a1", "A", "East", 500, null, null));
    BatchWithLink a2 =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("a2", "A", "West", 500, null, null));
    BatchWithLink b1 =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("b1", "B", "South", 1000, null, null));

    for (int i = 0; i < 50; i++) recordClick(a1.link().getId(), start.plusSeconds(60 + i), false);
    for (int i = 0; i < 70; i++) recordClick(a2.link().getId(), start.plusSeconds(60 + i), false);
    for (int i = 0; i < 30; i++) recordClick(b1.link().getId(), start.plusSeconds(60 + i), false);

    CampaignStatsResponse stats = statsService.statsFor(campaign.getId(), owner);

    assertThat(stats.byDistributor()).hasSize(2);
    CampaignStatsResponse.GroupStats top = stats.byDistributor().get(0);
    assertThat(top.key()).isEqualTo("A");
    assertThat(top.clicks()).isEqualTo(120L);
    assertThat(top.totalQuantity()).isEqualTo(1000);
    assertThat(top.clickRatePerHundred()).isEqualTo(12.0);
  }

  @Test
  void emptyCampaignReturnsZeroStats() {
    Long owner = newOwner("empty");
    Instant start = Instant.now();
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C", start, start.plusSeconds(3600), "https://example.com/d", null, null, null));

    CampaignStatsResponse stats = statsService.statsFor(campaign.getId(), owner);

    assertThat(stats.totalClicks()).isZero();
    assertThat(stats.byBatch()).isEmpty();
  }
}
