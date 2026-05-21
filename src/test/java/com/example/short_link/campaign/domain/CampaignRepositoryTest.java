package com.example.short_link.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignRepositoryTest {

  @Autowired private CampaignRepository repository;

  @Test
  void persistsAndReadsOwnerCampaignsNewestFirst() {
    Instant base = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity older =
        repository.save(
            new CampaignEntity(
                7L, "Old", base, base.plusSeconds(3600), null, CampaignPostEndAction.KEEP, null));
    CampaignEntity newer =
        repository.save(
            new CampaignEntity(
                7L,
                "New",
                base.plusSeconds(60),
                base.plusSeconds(7200),
                null,
                CampaignPostEndAction.REDIRECT,
                "https://post.example.com"));

    List<CampaignEntity> mine = repository.findByOwnerIdOrderByCreatedAtDesc(7L);

    assertThat(mine)
        .extracting(CampaignEntity::getId)
        .containsExactly(newer.getId(), older.getId());
  }

  @Test
  void findsDraftsWithStartReached() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    repository.save(
        new CampaignEntity(
            1L, "ready", now.minusSeconds(60), now.plusSeconds(3600), null, null, null));
    repository.save(
        new CampaignEntity(
            1L, "future", now.plusSeconds(60), now.plusSeconds(3600), null, null, null));

    List<CampaignEntity> due =
        repository.findByStatusAndStartsAtLessThanEqual(CampaignStatus.DRAFT, now);

    assertThat(due).extracting(CampaignEntity::getName).containsExactly("ready");
  }

  @Test
  void findsActivesPastEndsAt() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity expired =
        repository.save(
            new CampaignEntity(
                1L, "expired", now.minusSeconds(7200), now.minusSeconds(60), null, null, null));
    expired.activateIfStarted(now);
    repository.save(expired);
    CampaignEntity ongoing =
        repository.save(
            new CampaignEntity(
                1L, "ongoing", now.minusSeconds(7200), now.plusSeconds(3600), null, null, null));
    ongoing.activateIfStarted(now);
    repository.save(ongoing);

    List<CampaignEntity> due =
        repository.findByStatusAndEndsAtLessThanEqual(CampaignStatus.ACTIVE, now);

    assertThat(due).extracting(CampaignEntity::getName).containsExactly("expired");
  }
}
