package com.example.short_link.campaign.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ArchiveCampaignUseCaseTest {

  private final CampaignOwnership ownership = mock(CampaignOwnership.class);
  private final ArchiveCampaignUseCase useCase = new ArchiveCampaignUseCase(ownership);

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
  void executeMovesCampaignToArchived() {
    CampaignEntity c = owned();
    when(ownership.require(1L, 7L)).thenReturn(c);

    useCase.execute(1L, 7L);

    assertThat(c.getStatus()).isEqualTo(CampaignStatus.ARCHIVED);
  }

  @Test
  void executeReturnsSameEntity() {
    CampaignEntity c = owned();
    when(ownership.require(1L, 7L)).thenReturn(c);

    assertThat(useCase.execute(1L, 7L)).isSameAs(c);
  }
}
