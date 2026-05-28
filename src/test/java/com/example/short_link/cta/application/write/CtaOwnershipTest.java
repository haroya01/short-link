package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CtaOwnershipTest {

  @Mock private CtaRepository ctaRepository;

  private CtaOwnership ctaOwnership;

  @BeforeEach
  void setUp() {
    ctaOwnership = new CtaOwnership(ctaRepository);
  }

  @Test
  void requireOwnedReturnsCta() {
    CtaEntity cta = new CtaEntity(7L, "L", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    assertThat(ctaOwnership.requireOwned(7L, 42L)).isSameAs(cta);
  }

  @Test
  void notFoundThrows() {
    when(ctaRepository.findById(42L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ctaOwnership.requireOwned(7L, 42L))
        .isInstanceOf(CtaException.class)
        .extracting(e -> ((CtaException) e).errorCode())
        .isEqualTo(CtaErrorCode.CTA_NOT_FOUND);
  }

  @Test
  void foreignOwnerThrows() {
    CtaEntity cta = new CtaEntity(9L, "L", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    when(ctaRepository.findById(42L)).thenReturn(Optional.of(cta));

    assertThatThrownBy(() -> ctaOwnership.requireOwned(7L, 42L))
        .isInstanceOf(CtaException.class)
        .extracting(e -> ((CtaException) e).errorCode())
        .isEqualTo(CtaErrorCode.CTA_PERMISSION_DENIED);
  }
}
