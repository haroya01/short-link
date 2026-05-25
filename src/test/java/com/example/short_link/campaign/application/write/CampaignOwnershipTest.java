package com.example.short_link.campaign.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampaignOwnershipTest {

  private final CampaignRepository repository = mock(CampaignRepository.class);
  private final CampaignOwnership ownership = new CampaignOwnership(repository);

  private CampaignEntity ownedBy(Long ownerId) {
    return new CampaignEntity(
        ownerId,
        "x",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        "https://example.com",
        CampaignPostEndAction.KEEP,
        null,
        null);
  }

  @Test
  void requireReturnsCampaignWhenOwnedByUser() {
    CampaignEntity c = ownedBy(42L);
    when(repository.findById(1L)).thenReturn(Optional.of(c));

    assertThat(ownership.require(1L, 42L)).isSameAs(c);
  }

  @Test
  void requireThrowsWhenCampaignNotFound() {
    when(repository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ownership.require(1L, 42L)).isInstanceOf(CampaignException.class);
  }

  @Test
  void requireThrowsWhenOwnedByDifferentUser() {
    when(repository.findById(1L)).thenReturn(Optional.of(ownedBy(42L)));

    assertThatThrownBy(() -> ownership.require(1L, 99L)).isInstanceOf(CampaignException.class);
  }
}
