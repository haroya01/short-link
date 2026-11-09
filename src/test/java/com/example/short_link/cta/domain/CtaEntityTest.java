package com.example.short_link.cta.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CtaEntityTest {

  private CtaEntity sample() {
    return new CtaEntity(
        7L, "30 min consult", "https://cal.com/me", CtaStyle.PRIMARY, CtaPurpose.BOOKING);
  }

  @Test
  void constructorSetsFields() {
    CtaEntity cta = sample();
  }

  @Test
  void isOwnedByMatches() {
    CtaEntity cta = sample();
  }

  @Test
  void updateLabel() {
    CtaEntity cta = sample();
    cta.updateLabel("New Label");
  }

  @Test
  void updateUrlAndStyleAndPurpose() {
    CtaEntity cta = sample();
    cta.updateUrl("https://new.url");
    cta.updateStyle(CtaStyle.SECONDARY);
    cta.updatePurpose(CtaPurpose.SUBSCRIBE);
  }

  @Test
  void softDeleteStampsDeletedAt() {
    CtaEntity cta = sample();
    cta.softDelete();
  }

  @Test
  void softDeleteIsIdempotent() {
    CtaEntity cta = sample();
    cta.softDelete();
    Instant first = cta.getDeletedAt();
    cta.softDelete();
  }

  @Test
  void updateAfterDeleteIsRejected() {
    CtaEntity cta = sample();
    cta.softDelete();
    assertThatThrownBy(() -> cta.updateLabel("X"))
        .isInstanceOf(CtaException.class)
        .extracting(e -> ((CtaException) e).errorCode())
        .isEqualTo(CtaErrorCode.CTA_DELETED);
  }

  @Test
  void noFieldOfADeletedCtaCanBeEdited() {
    CtaEntity cta = sample();
    cta.softDelete();

    assertThatThrownBy(() -> cta.updateUrl("https://new")).isInstanceOf(CtaException.class);
    assertThatThrownBy(() -> cta.updateStyle(CtaStyle.SECONDARY)).isInstanceOf(CtaException.class);
    assertThatThrownBy(() -> cta.updatePurpose(CtaPurpose.SUBSCRIBE))
        .isInstanceOf(CtaException.class);
  }
}
