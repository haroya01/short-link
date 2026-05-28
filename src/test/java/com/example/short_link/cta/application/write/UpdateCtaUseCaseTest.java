package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCtaUseCaseTest {

  @Mock private CtaOwnership ctaOwnership;
  @Mock private CtaRepository ctaRepository;

  private UpdateCtaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateCtaUseCase(ctaOwnership, ctaRepository);
  }

  private CtaEntity owned() {
    return new CtaEntity(7L, "Old", "https://old", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
  }

  @Test
  void updatesLabelOnly() {
    CtaEntity cta = owned();
    when(ctaOwnership.requireOwned(7L, 42L)).thenReturn(cta);
    when(ctaRepository.save(any(CtaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    CtaEntity updated = useCase.execute(new UpdateCtaCommand(7L, 42L, "New", null, null, null));

    assertThat(updated.getLabel()).isEqualTo("New");
    assertThat(updated.getUrl()).isEqualTo("https://old");
  }

  @Test
  void updatesAllFields() {
    CtaEntity cta = owned();
    when(ctaOwnership.requireOwned(7L, 42L)).thenReturn(cta);
    when(ctaRepository.save(any(CtaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new UpdateCtaCommand(
            7L, 42L, "New", "https://new", CtaStyle.SECONDARY, CtaPurpose.SUBSCRIBE));

    assertThat(cta.getLabel()).isEqualTo("New");
    assertThat(cta.getUrl()).isEqualTo("https://new");
    assertThat(cta.getStyle()).isEqualTo(CtaStyle.SECONDARY);
    assertThat(cta.getPurpose()).isEqualTo(CtaPurpose.SUBSCRIBE);
  }

  @Test
  void rejectsBlankLabel() {
    assertThatThrownBy(() -> useCase.execute(new UpdateCtaCommand(7L, 42L, "  ", null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUpdateOfDeletedCta() {
    CtaEntity cta = owned();
    cta.softDelete();
    when(ctaOwnership.requireOwned(7L, 42L)).thenReturn(cta);

    assertThatThrownBy(
            () -> useCase.execute(new UpdateCtaCommand(7L, 42L, "New", null, null, null)))
        .isInstanceOf(CtaException.class)
        .extracting(e -> ((CtaException) e).errorCode())
        .isEqualTo(CtaErrorCode.CTA_DELETED);
  }
}
