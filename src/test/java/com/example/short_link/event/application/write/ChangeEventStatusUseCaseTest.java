package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.write.ChangeEventStatusUseCase.Action;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventStatus;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeEventStatusUseCaseTest {

  @Mock private EventRepository eventRepository;

  private ChangeEventStatusUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new ChangeEventStatusUseCase(eventRepository);
  }

  private EventEntity event() {
    return new EventEntity(
        1L,
        "slug123abc",
        "스터디",
        null,
        Instant.parse("2026-09-01T10:00:00Z"),
        null,
        "Asia/Seoul",
        null,
        null,
        null,
        null,
        null,
        ContactField.EMAIL);
  }

  @Test
  void missingEvent_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 10L, Action.CLOSE))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void otherOwnersEvent_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

    assertThatThrownBy(() -> useCase.execute(99L, 10L, Action.CANCEL))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void closeThenReopen_togglesStatus() {
    EventEntity event = event();
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

    assertThat(useCase.execute(1L, 10L, Action.valueOf("CLOSE")).getStatus())
        .isEqualTo(EventStatus.CLOSED);
    assertThat(useCase.execute(1L, 10L, Action.REOPEN).getStatus()).isEqualTo(EventStatus.OPEN);
  }

  @Test
  void cancel_isTerminal() {
    EventEntity event = event();
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

    assertThat(useCase.execute(1L, 10L, Action.CANCEL).getStatus()).isEqualTo(EventStatus.CANCELED);
    assertThatThrownBy(() -> useCase.execute(1L, 10L, Action.CLOSE))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_CANCELED);
  }
}
