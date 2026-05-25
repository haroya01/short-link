package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.repository.LinkRepository;
import org.junit.jupiter.api.Test;

class IncrementViewCountUseCaseTest {

  private final LinkRepository repository = mock(LinkRepository.class);
  private final IncrementViewCountUseCase useCase = new IncrementViewCountUseCase(repository);

  @Test
  void executeReturnsRowsUpdatedFromRepository() {
    when(repository.incrementViewCountIfBelowLimit(7L)).thenReturn(1);

    assertThat(useCase.execute(new IncrementViewCountCommand(7L))).isEqualTo(1);
  }

  @Test
  void executeReturnsZeroWhenLimitReached() {
    when(repository.incrementViewCountIfBelowLimit(7L)).thenReturn(0);

    assertThat(useCase.execute(new IncrementViewCountCommand(7L))).isZero();
  }

  @Test
  void commandRejectsNullLinkId() {
    assertThatThrownBy(() -> new IncrementViewCountCommand(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
