package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.write.EventLinkIssuer.IssuedLink;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CreateEventAliasLinkUseCaseTest {

  @Mock private EventRepository eventRepository;
  @Mock private EventLinkIssuer linkIssuer;

  private CreateEventAliasLinkUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateEventAliasLinkUseCase(eventRepository, linkIssuer);
  }

  private EventEntity event() {
    EventEntity event =
        new EventEntity(
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
    ReflectionTestUtils.setField(event, "id", 10L);
    return event;
  }

  @Test
  void missingEvent_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 10L, "단톡용"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void otherOwnersEvent_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

    assertThatThrownBy(() -> useCase.execute(99L, 10L, "단톡용"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void blankOrOversizedLabel_isRejected() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

    for (String label : new String[] {null, "   ", "가".repeat(51)}) {
      assertThatThrownBy(() -> useCase.execute(1L, 10L, label))
          .isInstanceOf(EventException.class)
          .extracting(e -> ((EventException) e).errorCode())
          .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
    }
  }

  @Test
  void trimmedLabel_isIssued() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
    when(linkIssuer.issue(1L, 10L, "slug123abc", "단톡용"))
        .thenReturn(Optional.of(new IssuedLink(7L, "abc1234")));

    IssuedLink issued = useCase.execute(1L, 10L, "  단톡용  ");

    assertThat(issued.linkId()).isEqualTo(7L);
    assertThat(issued.shortCode()).isEqualTo("abc1234");
  }

  @Test
  void issuerGivingUp_surfacesAsSlugExhausted() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
    when(linkIssuer.issue(1L, 10L, "slug123abc", "단톡용")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(1L, 10L, "단톡용"))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.SLUG_EXHAUSTED);
  }
}
