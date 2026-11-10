package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import org.junit.jupiter.api.Test;

class SpendViewUseCaseTest {

  private final LinkRepository repository = mock(LinkRepository.class);
  private final SpendViewUseCase useCase = new SpendViewUseCase(repository);

  @Test
  void aViewLeftIsSpent() {
    when(repository.spendView(new LinkId(7L))).thenReturn(true);

    assertThat(useCase.spend(new LinkId(7L))).isTrue();
  }

  @Test
  void noViewLeftIsRefused() {
    when(repository.spendView(new LinkId(7L))).thenReturn(false);

    assertThat(useCase.spend(new LinkId(7L))).isFalse();
  }
}
