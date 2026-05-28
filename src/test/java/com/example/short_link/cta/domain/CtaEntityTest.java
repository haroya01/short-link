package com.example.short_link.cta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import org.junit.jupiter.api.Test;

class CtaEntityTest {

  private CtaEntity sample() {
    return new CtaEntity(
        7L, "30 min consult", "https://cal.com/me", CtaStyle.PRIMARY, CtaPurpose.BOOKING);
  }

  @Test
  void constructorSetsFields() {
    CtaEntity cta = sample();
    assertThat(cta.getUserId()).isEqualTo(7L);
    assertThat(cta.getLabel()).isEqualTo("30 min consult");
    assertThat(cta.getUrl()).isEqualTo("https://cal.com/me");
    assertThat(cta.getStyle()).isEqualTo(CtaStyle.PRIMARY);
    assertThat(cta.getPurpose()).isEqualTo(CtaPurpose.BOOKING);
    assertThat(cta.isDeleted()).isFalse();
    assertThat(cta.getDeletedAt()).isNull();
  }

  @Test
  void isOwnedByMatches() {
    CtaEntity cta = sample();
    assertThat(cta.isOwnedBy(7L)).isTrue();
    assertThat(cta.isOwnedBy(9L)).isFalse();
  }

  @Test
  void updateLabel() {
    CtaEntity cta = sample();
    cta.updateLabel("New Label");
    assertThat(cta.getLabel()).isEqualTo("New Label");
  }

  @Test
  void updateUrlAndStyleAndPurpose() {
    CtaEntity cta = sample();
    cta.updateUrl("https://new.url");
    cta.updateStyle(CtaStyle.SECONDARY);
    cta.updatePurpose(CtaPurpose.SUBSCRIBE);
    assertThat(cta.getUrl()).isEqualTo("https://new.url");
    assertThat(cta.getStyle()).isEqualTo(CtaStyle.SECONDARY);
    assertThat(cta.getPurpose()).isEqualTo(CtaPurpose.SUBSCRIBE);
  }

  @Test
  void softDeleteStampsDeletedAt() {
    CtaEntity cta = sample();
    cta.softDelete();
    assertThat(cta.isDeleted()).isTrue();
    assertThat(cta.getDeletedAt()).isNotNull();
  }

  @Test
  void softDeleteIsIdempotent() {
    CtaEntity cta = sample();
    cta.softDelete();
    java.time.Instant first = cta.getDeletedAt();
    cta.softDelete();
    assertThat(cta.getDeletedAt()).isEqualTo(first);
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
}
