package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.exception.DestinationException;
import org.junit.jupiter.api.Test;

class UpdateDestinationUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final UpdateDestinationUseCase useCase = new UpdateDestinationUseCase(ownership);

  private LinkDestinationEntity dest() {
    LinkDestinationEntity d =
        new LinkDestinationEntity(1L, "https://old.example.com", 1, null, null, null, null);
    when(ownership.ownedDestination(7L, "abc", 99L)).thenReturn(d);
    return d;
  }

  @Test
  void executeUpdatesUrl() {
    LinkDestinationEntity d = dest();

    useCase.execute(7L, "abc", 99L, "https://new.example.com", null, null, null, null, null, null);

    assertThat(d.getUrl()).isEqualTo("https://new.example.com");
  }

  @Test
  void executeClampsWeightToValidRange() {
    LinkDestinationEntity d = dest();

    useCase.execute(7L, "abc", 99L, null, 999, null, null, null, null, null);

    assertThat(d.getWeight()).isLessThanOrEqualTo(100);
  }

  @Test
  void executeRejectsNonHttpUrl() {
    dest();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    7L, "abc", 99L, "javascript:alert(1)", null, null, null, null, null, null))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void executeTogglesEnabled() {
    LinkDestinationEntity d = dest();

    useCase.execute(7L, "abc", 99L, null, null, null, false, null, null, null);

    assertThat(d.isEnabled()).isFalse();
  }

  @Test
  void executeReturnsSummaryWithUpdatedUrl() {
    dest();

    var out =
        useCase.execute(
            7L, "abc", 99L, "https://new.example.com", null, null, null, null, null, null);

    assertThat(out.url()).isEqualTo("https://new.example.com");
  }
}
