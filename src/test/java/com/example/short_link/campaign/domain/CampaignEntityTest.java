package com.example.short_link.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CampaignEntityTest {

  private static CampaignEntity sample(Instant startsAt, Instant endsAt) {
    return new CampaignEntity(
        1L,
        "Sample",
        startsAt,
        endsAt,
        "https://dest.example.com",
        CampaignPostEndAction.KEEP,
        null,
        null);
  }

  @Test
  void draftActivatesOnceStartReached() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity c = sample(now.minusSeconds(60), now.plusSeconds(3600));
    assertThat(c.getStatus()).isEqualTo(CampaignStatus.DRAFT);

    c.activateIfStarted(now);

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
  }

  @Test
  void draftStaysDraftBeforeStart() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity c = sample(now.plusSeconds(60), now.plusSeconds(3600));

    c.activateIfStarted(now);

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.DRAFT);
  }

  @Test
  void endedRecordsEndedAtAndIsIdempotent() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity c = sample(now.minusSeconds(3600), now);

    c.markEnded(now);

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.ENDED);
    assertThat(c.getEndedAt()).isEqualTo(now);

    Instant later = now.plusSeconds(60);
    c.markEnded(later);

    assertThat(c.getEndedAt()).isEqualTo(now);
  }

  @Test
  void archivedSkipsEndedTransition() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity c = sample(now.minusSeconds(3600), now.minusSeconds(60));
    c.markEnded(now);
    c.archive();

    c.markEnded(now.plusSeconds(60));

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.ARCHIVED);
  }

  @Test
  void ownershipCheck() {
    CampaignEntity c = sample(Instant.now(), Instant.now().plusSeconds(60));

    assertThat(c.isOwnedBy(1L)).isTrue();
    assertThat(c.isOwnedBy(2L)).isFalse();
    assertThat(c.isOwnedBy(null)).isFalse();
  }

  @Test
  void updatePolicyKeepsKeepWhenNullPassed() {
    Instant start = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity c = sample(start, start.plusSeconds(60));

    c.updatePolicy(
        Instant.parse("2026-06-01T00:00:00Z"), "https://other.example.com", null, null, null);

    assertThat(c.getPostEndAction()).isEqualTo(CampaignPostEndAction.KEEP);
    assertThat(c.getDefaultDestinationUrl()).isEqualTo("https://other.example.com");
  }

  @Test
  void constructionRejectsInvalidPeriodAndRedirectWithoutDestination() {
    Instant start = Instant.parse("2026-05-22T01:00:00Z");
    assertThatThrownBy(() -> sample(start, start))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD));
    assertThatThrownBy(
            () ->
                new CampaignEntity(
                    1L,
                    "Sample",
                    start,
                    start.plusSeconds(60),
                    null,
                    CampaignPostEndAction.REDIRECT,
                    "  ",
                    null))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(CampaignErrorCode.MISSING_POST_END_DESTINATION));
  }

  @Test
  void rejectedPolicyDoesNotPartiallyChangeTheCampaign() {
    Instant start = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity campaign = sample(start, start.plusSeconds(60));

    assertThatThrownBy(
            () ->
                campaign.updatePolicy(
                    start.plusSeconds(120),
                    "https://changed.example.com",
                    CampaignPostEndAction.REDIRECT,
                    " ",
                    "changed"))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(CampaignErrorCode.MISSING_POST_END_DESTINATION));

    assertThat(campaign.getEndsAt()).isEqualTo(start.plusSeconds(60));
    assertThat(campaign.getDefaultDestinationUrl()).isEqualTo("https://dest.example.com");
    assertThat(campaign.getPostEndAction()).isEqualTo(CampaignPostEndAction.KEEP);
    assertThat(campaign.getPostEndMessage()).isNull();
  }

  @Test
  void archivedPolicyIsRejectedBeforeOtherInvalidFields() {
    Instant start = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity campaign = sample(start, start.plusSeconds(60));
    campaign.archive();

    assertThatThrownBy(
            () -> campaign.updatePolicy(start, null, CampaignPostEndAction.REDIRECT, null, null))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_ARCHIVED));
  }

  @Test
  void messageIsNormalizedAndRetainedForActionsThatDoNotApplyIt() {
    Instant start = Instant.parse("2026-05-22T01:00:00Z");
    CampaignEntity campaign = sample(start, start.plusSeconds(60));
    campaign.updatePolicy(
        start.plusSeconds(120),
        null,
        CampaignPostEndAction.KEEP,
        null,
        "  " + "m".repeat(501) + "  ");
    assertThat(campaign.getPostEndMessage()).isEqualTo("m".repeat(500));

    campaign.updatePolicy(start.plusSeconds(120), null, CampaignPostEndAction.EXPIRE, null, "  ");
    assertThat(campaign.getPostEndMessage()).isNull();
  }
}
