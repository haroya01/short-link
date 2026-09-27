package com.example.short_link.campaign.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UpdateCampaignPolicyUseCaseTest {

  private static final Instant START = Instant.parse("2026-05-22T01:00:00Z");
  private final CampaignOwnership ownership = mock(CampaignOwnership.class);
  private final UpdateCampaignPolicyUseCase useCase = new UpdateCampaignPolicyUseCase(ownership);

  private static Stream<String> invalidUpdatedNames() {
    return Stream.of("", " \t\n", "n".repeat(256));
  }

  private CampaignEntity campaign() {
    CampaignEntity campaign =
        new CampaignEntity(
            7L,
            "original",
            START,
            START.plusSeconds(3600),
            "https://default.example.com",
            CampaignPostEndAction.REDIRECT,
            "https://after.example.com",
            "later message");
    when(ownership.require(1L, 7L)).thenReturn(campaign);
    return campaign;
  }

  @Test
  void unspecifiedFieldsKeepExistingPolicyAndMessage() {
    CampaignEntity campaign = campaign();

    useCase.execute(
        new UpdateCampaignPolicyCommand(1L, 7L, "renamed", null, null, null, null, null));

    assertThat(campaign.getName()).isEqualTo("renamed");
    assertThat(campaign.getEndsAt()).isEqualTo(START.plusSeconds(3600));
    assertThat(campaign.getDefaultDestinationUrl()).isEqualTo("https://default.example.com");
    assertThat(campaign.getPostEndAction()).isEqualTo(CampaignPostEndAction.REDIRECT);
    assertThat(campaign.getPostEndDestinationUrl()).isEqualTo("https://after.example.com");
    assertThat(campaign.getPostEndMessage()).isEqualTo("later message");
  }

  @Test
  void actionChangeUsesTheExistingDestinationAndBlankMessageClearsIt() {
    CampaignEntity campaign = campaign();
    campaign.updatePolicy(
        START.plusSeconds(3600),
        null,
        CampaignPostEndAction.KEEP,
        "https://after.example.com",
        "later message");

    useCase.execute(
        new UpdateCampaignPolicyCommand(
            1L, 7L, null, null, null, CampaignPostEndAction.REDIRECT, null, "  "));

    assertThat(campaign.getName()).isEqualTo("original");
    assertThat(campaign.getPostEndAction()).isEqualTo(CampaignPostEndAction.REDIRECT);
    assertThat(campaign.getPostEndDestinationUrl()).isEqualTo("https://after.example.com");
    assertThat(campaign.getPostEndMessage()).isNull();
  }

  @Test
  void invalidFinalPolicyLeavesTheNameAndPreviousPolicyIntact() {
    CampaignEntity campaign = campaign();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdateCampaignPolicyCommand(
                        1L, 7L, "renamed", null, null, null, " ", null)))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e ->
                assertThat(e.errorCode())
                    .isEqualTo(CampaignErrorCode.MISSING_POST_END_DESTINATION));

    assertThat(campaign.getName()).isEqualTo("original");
    assertThat(campaign.getPostEndDestinationUrl()).isEqualTo("https://after.example.com");
  }

  @ParameterizedTest
  @MethodSource("invalidUpdatedNames")
  void invalidNameLeavesTheEntirePreviousPolicyIntact(String name) {
    CampaignEntity campaign = campaign();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UpdateCampaignPolicyCommand(
                        1L,
                        7L,
                        name,
                        START.plusSeconds(7200),
                        "https://changed.example.com",
                        CampaignPostEndAction.EXPIRE,
                        "https://changed-after.example.com",
                        "changed message")))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CampaignErrorCode.INVALID_CAMPAIGN_NAME));

    assertThat(campaign.getName()).isEqualTo("original");
    assertThat(campaign.getEndsAt()).isEqualTo(START.plusSeconds(3600));
    assertThat(campaign.getDefaultDestinationUrl()).isEqualTo("https://default.example.com");
    assertThat(campaign.getPostEndAction()).isEqualTo(CampaignPostEndAction.REDIRECT);
    assertThat(campaign.getPostEndDestinationUrl()).isEqualTo("https://after.example.com");
    assertThat(campaign.getPostEndMessage()).isEqualTo("later message");
  }
}
