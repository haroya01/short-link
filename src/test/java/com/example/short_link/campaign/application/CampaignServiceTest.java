package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.api.CampaignCreateRequest;
import com.example.short_link.campaign.api.CampaignUpdateRequest;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
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
class CampaignServiceTest {

  @Autowired
  private com.example.short_link.campaign.application.write.CreateCampaignUseCase createUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.UpdateCampaignPolicyUseCase
      updateUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.ArchiveCampaignUseCase archiveUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.ActivateReadyCampaignsUseCase
      activateUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.EndCampaignNowUseCase endNowUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.ReapplyCampaignPolicyUseCase
      reapplyUseCase;

  @Autowired private com.example.short_link.campaign.application.read.CampaignQueryService query;

  @Test
  void createsCampaignAndActivatesImmediatelyWhenStartIsPast() {
    CampaignCreateRequest req =
        new CampaignCreateRequest(
            "now-start",
            Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600),
            "https://example.com",
            CampaignPostEndAction.KEEP,
            null,
            null);

    CampaignEntity created = createUseCase.execute(100L, req);

    assertThat(created.getId()).isNotNull();
    assertThat(created.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
  }

  @Test
  void createsCampaignAsDraftWhenStartInFuture() {
    CampaignCreateRequest req =
        new CampaignCreateRequest(
            "future",
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(3600),
            null,
            null,
            null,
            null);

    CampaignEntity created = createUseCase.execute(100L, req);

    assertThat(created.getStatus()).isEqualTo(CampaignStatus.DRAFT);
    assertThat(created.getPostEndAction()).isEqualTo(CampaignPostEndAction.KEEP);
  }

  @Test
  void rejectsEndsAtBeforeOrEqualToStartsAt() {
    Instant start = Instant.parse("2026-06-01T00:00:00Z");
    CampaignCreateRequest req =
        new CampaignCreateRequest("bad", start, start, null, null, null, null);

    assertThatThrownBy(() -> createUseCase.execute(100L, req))
        .isInstanceOf(InvalidCampaignPeriodException.class);
  }

  @Test
  void rejectsRedirectWithoutDestination() {
    CampaignCreateRequest req =
        new CampaignCreateRequest(
            "redir",
            null,
            Instant.now().plusSeconds(3600),
            null,
            CampaignPostEndAction.REDIRECT,
            "  ",
            null);

    assertThatThrownBy(() -> createUseCase.execute(100L, req))
        .isInstanceOf(MissingPostEndDestinationException.class);
  }

  @Test
  void detailFailsForOtherOwnerWith404Semantic() {
    CampaignEntity created =
        createUseCase.execute(
            200L,
            new CampaignCreateRequest(
                "mine", null, Instant.now().plusSeconds(3600), null, null, null, null));

    assertThatThrownBy(() -> query.detail(created.getId(), 999L))
        .isInstanceOf(CampaignNotOwnedException.class);
    assertThatThrownBy(() -> query.detail(-1L, 200L)).isInstanceOf(CampaignNotFoundException.class);
  }

  @Test
  void updatePolicyChangesFieldsAndKeepsImmutableStartsAt() {
    CampaignEntity created =
        createUseCase.execute(
            300L,
            new CampaignCreateRequest(
                "orig", null, Instant.now().plusSeconds(3600), null, null, null, null));
    Instant originalStart = created.getStartsAt();

    Instant newEnd = Instant.now().plusSeconds(7200);
    CampaignEntity updated =
        updateUseCase.execute(
            created.getId(),
            300L,
            new CampaignUpdateRequest(
                "renamed",
                newEnd,
                "https://new.example.com",
                CampaignPostEndAction.REDIRECT,
                "https://post.example.com",
                null));

    assertThat(updated.getName()).isEqualTo("renamed");
    assertThat(updated.getEndsAt()).isEqualTo(newEnd);
    assertThat(updated.getPostEndAction()).isEqualTo(CampaignPostEndAction.REDIRECT);
    assertThat(updated.getStartsAt()).isEqualTo(originalStart);
  }

  @Test
  void archiveBlocksFurtherPolicyUpdates() {
    CampaignEntity created =
        createUseCase.execute(
            400L,
            new CampaignCreateRequest(
                "arch", null, Instant.now().plusSeconds(3600), null, null, null, null));
    archiveUseCase.execute(created.getId(), 400L);

    assertThatThrownBy(
            () ->
                updateUseCase.execute(
                    created.getId(),
                    400L,
                    new CampaignUpdateRequest("x", null, null, null, null, null)))
        .isInstanceOf(CampaignArchivedException.class);
  }

  @Test
  void activateReadyPromotesPastDraftsOnly() {
    Instant now = Instant.now();
    CampaignEntity future =
        createUseCase.execute(
            500L,
            new CampaignCreateRequest(
                "future", now.plusSeconds(60), now.plusSeconds(3600), null, null, null, null));
    CampaignEntity past =
        createUseCase.execute(
            500L,
            new CampaignCreateRequest(
                "past", now.minusSeconds(120), now.plusSeconds(3600), null, null, null, null));

    int activated = activateUseCase.execute(now);

    assertThat(activated).isGreaterThanOrEqualTo(0);
    List<CampaignEntity> mine = query.list(500L);
    assertThat(mine)
        .filteredOn(c -> c.getId().equals(future.getId()))
        .extracting(CampaignEntity::getStatus)
        .containsExactly(CampaignStatus.DRAFT);
    assertThat(mine)
        .filteredOn(c -> c.getId().equals(past.getId()))
        .extracting(CampaignEntity::getStatus)
        .containsExactly(CampaignStatus.ACTIVE);
  }
}
