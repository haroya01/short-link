package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.dto.CampaignSummary;
import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.application.write.EndCampaignNowUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.presentation.request.CampaignBatchCreateRequest;
import com.example.short_link.campaign.presentation.request.CampaignCreateRequest;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class CampaignSummariesIntegrationTest {

  @Autowired private CampaignQueryService query;
  @Autowired private CampaignStatsService statsService;
  @Autowired private CampaignBatchService batchService;
  @Autowired private CreateCampaignUseCase createCampaign;
  @Autowired private EndCampaignNowUseCase endCampaign;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @PersistenceContext private EntityManager em;

  private Long ownerId;

  private CampaignEntity campaign(String name, Instant startsAt) {
    return createCampaign.execute(
        new CampaignCreateRequest(
                name,
                startsAt,
                startsAt.plus(30, ChronoUnit.DAYS),
                "https://example.com/d",
                null,
                null,
                null)
            .toCommand(ownerId));
  }

  private LinkId batch(CampaignEntity campaign, int quantity) {
    BatchWithLink batch =
        batchService.create(
            campaign.getId(),
            ownerId,
            new CampaignBatchCreateRequest("b" + quantity, "A", "East", quantity, null, null)
                .toCommand());
    return batch.link().linkId();
  }

  private void click(LinkId link, Instant at, boolean bot) {
    clicks.save(ClickEventEntity.builder().linkId(link).clickedAt(at).bot(bot).build());
  }

  private CampaignSummary summaryOf(List<CampaignSummary> summaries, CampaignEntity campaign) {
    return summaries.stream()
        .filter(s -> s.campaign().getId().equals(campaign.getId()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void eachCampaignCountsHumanClicksFromItsOwnStartAndSumsItsBatchQuantities() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ownerId = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix)).getId();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant earlyStart = now.minus(10, ChronoUnit.DAYS);
    Instant lateStart = now.minus(3, ChronoUnit.DAYS);

    CampaignEntity early = campaign("early", earlyStart);
    LinkId station = batch(early, 100);
    LinkId museum = batch(early, 50);
    click(station, earlyStart.minusSeconds(60), false);
    click(station, earlyStart.plusSeconds(60), false);
    click(station, now.minusSeconds(60), false);
    click(station, now.minusSeconds(30), true);
    click(museum, lateStart.minusSeconds(60), false);

    CampaignEntity late = campaign("late", lateStart);
    LinkId park = batch(late, 30);
    click(park, lateStart.minusSeconds(60), false);
    click(park, lateStart.plusSeconds(60), false);

    CampaignEntity empty = campaign("empty", now.minus(1, ChronoUnit.DAYS));
    endCampaign.execute(early.getId(), ownerId);
    em.flush();

    List<CampaignSummary> summaries = query.summaries(ownerId);

    CampaignSummary earlySummary = summaryOf(summaries, early);
    assertThat(earlySummary.batchCount()).isEqualTo(2);
    assertThat(earlySummary.totalQuantity()).isEqualTo(150);
    assertThat(earlySummary.humanClicks()).isEqualTo(3);
    assertThat(earlySummary.campaign().getEndedAt()).isNotNull();

    CampaignSummary lateSummary = summaryOf(summaries, late);
    assertThat(lateSummary.batchCount()).isEqualTo(1);
    assertThat(lateSummary.totalQuantity()).isEqualTo(30);
    assertThat(lateSummary.humanClicks()).isEqualTo(1);
    assertThat(lateSummary.campaign().getEndedAt()).isNull();

    CampaignSummary emptySummary = summaryOf(summaries, empty);
    assertThat(emptySummary.batchCount()).isZero();
    assertThat(emptySummary.totalQuantity()).isZero();
    assertThat(emptySummary.humanClicks()).isZero();

    for (CampaignEntity campaign : List.of(early, late, empty)) {
      assertThat(summaryOf(summaries, campaign).humanClicks())
          .isEqualTo(statsService.statsFor(campaign.getId(), ownerId).totalClicks());
    }
  }

  @Test
  void ownerWithoutCampaignsGetsAnEmptyList() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    ownerId = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix)).getId();

    assertThat(query.summaries(ownerId)).isEmpty();
  }
}
