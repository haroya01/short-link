package com.example.short_link.link.og.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkOgMetadataEntityTest {

  @Test
  void nullLinkIdYieldsNullTypedAccessor() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity((LinkId) null);
    assertThat(entity.linkId()).isNull();
  }

  @Test
  void linkIdRoundTripsThroughTypedAccessor() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(42L));
    assertThat(entity.linkId()).isEqualTo(new LinkId(42L));
  }

  @Test
  void applyFetchedSetsStatusOkAndIncrementsAttempts() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    Instant when = Instant.parse("2026-05-28T00:00:00Z");

    entity.applyFetched("T", "D", "https://img", when);

    assertThat(entity.getOgTitle()).isEqualTo("T");
    assertThat(entity.getOgDescription()).isEqualTo("D");
    assertThat(entity.getOgImage()).isEqualTo("https://img");
    assertThat(entity.getOgFetchStatus()).isEqualTo("OK");
    assertThat(entity.getOgFetchedAt()).isEqualTo(when);
    assertThat(entity.getOgFetchAttempts()).isEqualTo(1);
  }

  @Test
  void markFetchFailedWithRetryUsesRetryableStatus() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    Instant when = Instant.parse("2026-05-28T00:00:00Z");

    entity.markFetchFailed(when, true);

    assertThat(entity.getOgFetchStatus()).isEqualTo("RETRYABLE");
    assertThat(entity.getOgFetchAttempts()).isEqualTo(1);
  }

  @Test
  void markFetchFailedTerminalUsesErrorStatus() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    entity.markFetchFailed(Instant.now(), false);
    assertThat(entity.getOgFetchStatus()).isEqualTo("ERROR");
  }

  @Test
  void changeOverrideTrimsAndNormalizesBlankToNull() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));

    entity.changeOverride("  Title  ", "  ", null);

    assertThat(entity.getOgTitleOverride()).isEqualTo("Title");
    assertThat(entity.getOgDescriptionOverride()).isNull();
    assertThat(entity.getOgImageOverride()).isNull();
  }

  @Test
  void resetForNewUrlClearsFetchedFieldsAndStatus() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    entity.applyFetched("T", "D", "img", Instant.now());

    entity.resetForNewUrl();

    assertThat(entity.getOgTitle()).isNull();
    assertThat(entity.getOgDescription()).isNull();
    assertThat(entity.getOgImage()).isNull();
    assertThat(entity.getOgFetchedAt()).isNull();
    assertThat(entity.getOgFetchStatus()).isEqualTo("PENDING");
  }

  @Test
  void effectiveValuesPreferOverridesWhenPresent() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    entity.applyFetched("BaseT", "BaseD", "base.png", Instant.now());
    entity.changeOverride("OverT", "OverD", "over.png");

    assertThat(entity.effectiveTitle()).isEqualTo("OverT");
    assertThat(entity.effectiveDescription()).isEqualTo("OverD");
    assertThat(entity.effectiveImage()).isEqualTo("over.png");
  }

  @Test
  void effectiveValuesFallBackToFetchedWhenOverrideBlank() {
    LinkOgMetadataEntity entity = new LinkOgMetadataEntity(new LinkId(1L));
    entity.applyFetched("BaseT", "BaseD", "base.png", Instant.now());

    assertThat(entity.effectiveTitle()).isEqualTo("BaseT");
    assertThat(entity.effectiveDescription()).isEqualTo("BaseD");
    assertThat(entity.effectiveImage()).isEqualTo("base.png");
  }
}
