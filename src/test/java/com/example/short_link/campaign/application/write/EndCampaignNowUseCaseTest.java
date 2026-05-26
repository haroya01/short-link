package com.example.short_link.campaign.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EndCampaignNowUseCaseTest {

  private final CampaignOwnership ownership = mock(CampaignOwnership.class);
  private final BatchPolicyApplier applier = mock(BatchPolicyApplier.class);
  private final EndCampaignNowUseCase useCase = new EndCampaignNowUseCase(ownership, applier);

  private CampaignEntity owned() {
    return new CampaignEntity(
        7L,
        "n",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        "https://example.com",
        CampaignPostEndAction.KEEP,
        null,
        null);
  }

  @Test
  void executeTransitionsActiveToEnded() {
    CampaignEntity c = owned();
    c.activateIfStarted(Instant.now());
    when(ownership.require(1L, 7L)).thenReturn(c);

    useCase.execute(1L, 7L);

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.ENDED);
  }

  @Test
  void executeAppliesBatchPolicyOnNewEnd() {
    CampaignEntity c = owned();
    when(ownership.require(1L, 7L)).thenReturn(c);

    useCase.execute(1L, 7L);

    verify(applier, times(1)).apply(eq(c), any(Instant.class));
  }

  @Test
  void executeRejectsArchivedCampaign() {
    CampaignEntity c = owned();
    c.archive();
    when(ownership.require(1L, 7L)).thenReturn(c);

    assertThatThrownBy(() -> useCase.execute(1L, 7L))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_ARCHIVED));
  }

  @Test
  void executeDoesNotApplyPolicyWhenArchived() {
    CampaignEntity c = owned();
    c.archive();
    when(ownership.require(1L, 7L)).thenReturn(c);

    try {
      useCase.execute(1L, 7L);
    } catch (CampaignException ignored) {
    }

    verify(applier, never()).apply(any(), any());
  }

  @Test
  void executeReappliesPolicyWhenAlreadyEndedWithoutChangingStatus() {
    CampaignEntity c = owned();
    Instant endedAt = Instant.parse("2026-05-01T00:00:00Z");
    c.markEnded(endedAt);
    when(ownership.require(1L, 7L)).thenReturn(c);

    useCase.execute(1L, 7L);

    verify(applier, times(1)).apply(c, endedAt);
  }
}
