package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.dto.CampaignStatsView;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.presentation.request.CampaignBatchCreateRequest;
import com.example.short_link.campaign.presentation.request.CampaignCreateRequest;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class CampaignStatsOwnerZoneIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
  private static final Instant BEFORE_DST_END = Instant.parse("2026-10-31T04:30:00Z");
  private static final Instant AFTER_DST_END = Instant.parse("2026-11-05T04:59:59Z");

  @Autowired private CampaignStatsService statsService;
  @Autowired private CampaignBatchService batchService;
  @Autowired private CreateCampaignUseCase createCampaign;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @PersistenceContext private EntityManager em;
  @MockitoSpyBean private Clock clock;

  private Long campaignWithClicks(UserEntity owner) {
    CampaignEntity campaign =
        createCampaign.execute(
            new CampaignCreateRequest(
                    "zone",
                    Instant.parse("2026-10-20T00:00:00Z"),
                    Instant.parse("2026-11-30T00:00:00Z"),
                    "https://example.com/d",
                    null,
                    null,
                    null)
                .toCommand(owner.getId()));
    BatchWithLink batch =
        batchService.create(
            campaign.getId(),
            owner.getId(),
            new CampaignBatchCreateRequest("east", "A", "East", 100, null, null).toCommand());
    for (Instant at : new Instant[] {BEFORE_DST_END, AFTER_DST_END}) {
      clicks.save(
          ClickEventEntity.builder()
              .linkId(batch.link().linkId())
              .clickedAt(at)
              .bot(false)
              .build());
    }
    em.flush();
    return campaign.getId();
  }

  @Test
  void timeBucketsUseTheOwnersZoneAndItsOffsetAtEachClick() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    owner.changeTimezone("America/New_York");
    Long campaignId = campaignWithClicks(owner);

    CampaignStatsView stats = statsService.statsFor(campaignId, owner.getId());

    assertThat(stats.timeBuckets().timezone()).isEqualTo("America/New_York");
    assertThat(stats.timeBuckets().byDay())
        .extracting(CampaignStatsView.DayBucket::day, CampaignStatsView.DayBucket::clicks)
        .containsExactly(
            tuple(LocalDate.parse("2026-10-31"), 1L), tuple(LocalDate.parse("2026-11-04"), 1L));
    assertThat(stats.timeBuckets().byHour())
        .extracting(CampaignStatsView.HourBucket::hour, CampaignStatsView.HourBucket::clicks)
        .containsExactly(tuple(0, 1L), tuple(23, 1L));
    assertThat(stats.timeBuckets().heatmap())
        .extracting(
            CampaignStatsView.HeatmapCell::dayOfWeek,
            CampaignStatsView.HeatmapCell::hour,
            CampaignStatsView.HeatmapCell::clicks)
        .containsExactly(tuple(4, 23, 1L), tuple(7, 0, 1L));
  }

  @Test
  void ownersWithoutAZoneChangeKeepSeoulBuckets() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    Long campaignId = campaignWithClicks(owner);

    CampaignStatsView stats = statsService.statsFor(campaignId, owner.getId());

    assertThat(stats.timeBuckets().timezone()).isEqualTo("Asia/Seoul");
    assertThat(stats.timeBuckets().byDay())
        .extracting(CampaignStatsView.DayBucket::day, CampaignStatsView.DayBucket::clicks)
        .containsExactly(
            tuple(LocalDate.parse("2026-10-31"), 1L), tuple(LocalDate.parse("2026-11-05"), 1L));
    assertThat(stats.timeBuckets().byHour())
        .extracting(CampaignStatsView.HourBucket::hour, CampaignStatsView.HourBucket::clicks)
        .containsExactly(tuple(13, 2L));
  }
}
