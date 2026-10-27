package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import com.example.short_link.support.TestEntities;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LinkSidecarsTest {

  private static final Instant AT = Instant.parse("2026-10-27T00:00:00Z");

  private final LinkAccessControlRepository accessControls =
      mock(LinkAccessControlRepository.class);
  private final LinkExpirationPolicyRepository policies =
      mock(LinkExpirationPolicyRepository.class);
  private final LinkOgMetadataRepository ogMetadata = mock(LinkOgMetadataRepository.class);
  private final LinkSidecars sidecars = new LinkSidecars(accessControls, policies, ogMetadata);
  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://target", "abc1234", 7L, null), 3L);

  @BeforeEach
  void saveReturnsWhatItIsGiven() {
    when(accessControls.save(any())).thenAnswer(call -> call.getArgument(0));
    when(policies.save(any())).thenAnswer(call -> call.getArgument(0));
    when(ogMetadata.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  private LinkAccessControlEntity savedAccess() {
    ArgumentCaptor<LinkAccessControlEntity> saved =
        ArgumentCaptor.forClass(LinkAccessControlEntity.class);
    verify(accessControls).save(saved.capture());
    return saved.getValue();
  }

  private LinkExpirationPolicyEntity savedPolicy() {
    ArgumentCaptor<LinkExpirationPolicyEntity> saved =
        ArgumentCaptor.forClass(LinkExpirationPolicyEntity.class);
    verify(policies).save(saved.capture());
    return saved.getValue();
  }

  private LinkOgMetadataEntity savedOg() {
    ArgumentCaptor<LinkOgMetadataEntity> saved =
        ArgumentCaptor.forClass(LinkOgMetadataEntity.class);
    verify(ogMetadata).save(saved.capture());
    return saved.getValue();
  }

  @Test
  void aMissingSidecarIsCreatedForTheLinkAndSaved() {
    link.setMaxViews(5);

    sidecars.mirrorMaxViews(link);

    assertThat(savedAccess())
        .satisfies(
            access -> {
              assertThat(access.linkId()).isEqualTo(link.linkId());
              assertThat(access.getMaxViews()).isEqualTo(5);
            });
  }

  @Test
  void mirroringTheViewLimitLeavesTheStoredPasswordHashAlone() {
    LinkAccessControlEntity stored = new LinkAccessControlEntity(link.linkId());
    stored.changePasswordHash("stored-hash");
    when(accessControls.findById(3L)).thenReturn(Optional.of(stored));
    link.setPasswordHash("link-hash");
    link.setMaxViews(5);

    sidecars.mirrorMaxViews(link);

    assertThat(savedAccess().getPasswordHash()).isEqualTo("stored-hash");
    assertThat(stored.getMaxViews()).isEqualTo(5);
  }

  @Test
  void mirroringThePasswordCarriesTheHashAndTheViewLimit() {
    link.setPasswordHash("link-hash");
    link.setMaxViews(5);

    sidecars.mirrorPasswordAndMaxViews(link);

    assertThat(savedAccess().getPasswordHash()).isEqualTo("link-hash");
  }

  @Test
  void mirroringTheExpiredMessageLeavesTheStoredRedirectAlone() {
    LinkEntity before =
        TestEntities.withId(new LinkEntity("https://target", "abc1234", 7L, null), 3L);
    before.changeExpiredRedirectUrl("https://stored.example");
    LinkExpirationPolicyEntity stored = new LinkExpirationPolicyEntity(link.linkId());
    stored.mirrorExpiredRedirectUrl(before);
    when(policies.findById(3L)).thenReturn(Optional.of(stored));
    link.updateExpiredMessage("over");
    link.changeExpiredRedirectUrl("https://link.example");

    sidecars.mirrorExpiredMessage(link);

    assertThat(savedPolicy().getExpiredMessage()).isEqualTo("over");
    assertThat(stored.getExpiredRedirectUrl()).isEqualTo("https://stored.example");
  }

  @Test
  void aCampaignEndCarriesTheMessageAndTheRedirect() {
    link.applyCampaignExpiration(AT, " https://next.example ", " over ");

    sidecars.mirrorCampaignExpiration(link);

    LinkExpirationPolicyEntity policy = savedPolicy();
    assertThat(policy.getExpiredMessage()).isEqualTo("over");
    assertThat(policy.getExpiredRedirectUrl()).isEqualTo("https://next.example");
    assertThat(policy.getBlockedCountries()).isNull();
  }

  @Test
  void blockedCountriesAreMirroredAlone() {
    link.setBlockedCountries("kr,jp");
    link.updateExpiredMessage("over");

    sidecars.mirrorBlockedCountries(link);

    LinkExpirationPolicyEntity policy = savedPolicy();
    assertThat(policy.getBlockedCountries()).isEqualTo("KR,JP");
    assertThat(policy.getExpiredMessage()).isNull();
  }

  @Test
  void aNewDestinationClearsTheFetchedCardButKeepsTheOverride() {
    LinkOgMetadataEntity stored = new LinkOgMetadataEntity(link.linkId());
    stored.applyFetched("Old", "old", "old.png", AT);
    link.changeOgOverride("Mine", null, null);
    stored.mirrorOverride(link);
    when(ogMetadata.findById(3L)).thenReturn(Optional.of(stored));

    sidecars.resetOgForNewUrl(link);

    LinkOgMetadataEntity og = savedOg();
    assertThat(og.getOgTitle()).isNull();
    assertThat(og.getOgFetchStatus()).isEqualTo("PENDING");
    assertThat(og.getOgTitleOverride()).isEqualTo("Mine");
  }

  @Test
  void anOverrideIsMirroredAsTheLinkKeepsIt() {
    link.changeOgOverride("  Mine  ", " ", "https://img");

    sidecars.mirrorOgOverride(link);

    LinkOgMetadataEntity og = savedOg();
    assertThat(og.getOgTitleOverride()).isEqualTo("Mine");
    assertThat(og.getOgDescriptionOverride()).isNull();
    assertThat(og.getOgImageOverride()).isEqualTo("https://img");
  }

  @Test
  void aFetchResultIsRecordedOnTheSidecar() {
    sidecars.recordOgFetched(link, "T", "D", "https://img", AT);

    LinkOgMetadataEntity og = savedOg();
    assertThat(og.getOgTitle()).isEqualTo("T");
    assertThat(og.getOgFetchStatus()).isEqualTo("OK");
    assertThat(og.getOgFetchAttempts()).isEqualTo(1);
  }

  @Test
  void aFailedFetchIsRecordedAsRetryableOrNot() {
    sidecars.recordOgFetchFailed(link, AT, true);

    LinkOgMetadataEntity og = savedOg();
    assertThat(og.getOgFetchStatus()).isEqualTo("RETRYABLE");
    assertThat(og.getOgFetchedAt()).isEqualTo(AT);
  }
}
