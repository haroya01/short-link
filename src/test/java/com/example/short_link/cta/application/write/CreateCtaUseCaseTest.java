package com.example.short_link.cta.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
class CreateCtaUseCaseTest {

  @Mock private CtaRepository ctaRepository;

  private CreateCtaUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateCtaUseCase(ctaRepository);
  }

  @Test
  void createsCta() {
    when(ctaRepository.save(any(CtaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    CtaEntity created =
        useCase.execute(
            new CreateCtaCommand(
                7L, "30 min consult", "https://cal.com/me", CtaStyle.PRIMARY, CtaPurpose.BOOKING));

    assertThat(created.getUserId()).isEqualTo(7L);
    assertThat(created.getLabel()).isEqualTo("30 min consult");
    assertThat(created.getPurpose()).isEqualTo(CtaPurpose.BOOKING);
  }

  @Test
  void rejectsBlankLabel() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new CreateCtaCommand(
                        7L, "  ", "https://x", CtaStyle.PRIMARY, CtaPurpose.CUSTOM)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonHttpUrl() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new CreateCtaCommand(
                        7L, "L", "javascript:alert(1)", CtaStyle.PRIMARY, CtaPurpose.CUSTOM)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defaultsStyleAndPurposeWhenNull() {
    when(ctaRepository.save(any(CtaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    CtaEntity created = useCase.execute(new CreateCtaCommand(7L, "L", "https://x", null, null));

    assertThat(created.getStyle()).isEqualTo(CtaStyle.PRIMARY);
    assertThat(created.getPurpose()).isEqualTo(CtaPurpose.CUSTOM);
  }
}
