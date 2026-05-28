package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteCtaUseCaseTest {

  @Mock private CtaOwnership ctaOwnership;
  @Mock private CtaRepository ctaRepository;

  private DeleteCtaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteCtaUseCase(ctaOwnership, ctaRepository);
  }

  @Test
  void softDeletes() {
    CtaEntity cta = new CtaEntity(7L, "L", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM);
    when(ctaOwnership.requireOwned(7L, 42L)).thenReturn(cta);

    useCase.execute(new DeleteCtaCommand(7L, 42L));

    assertThat(cta.isDeleted()).isTrue();
    verify(ctaRepository).save(any(CtaEntity.class));
  }
}
