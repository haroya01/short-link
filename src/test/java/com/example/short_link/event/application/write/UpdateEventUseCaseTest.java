package com.example.short_link.event.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UpdateEventUseCaseTest {

  private static final Instant STARTS = Instant.parse("2026-09-01T10:00:00Z");

  @Mock private EventRepository eventRepository;
  @Mock private EventQuestionRepository questionRepository;
  @Mock private EventRegistrationRepository registrationRepository;

  private UpdateEventUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateEventUseCase(eventRepository, questionRepository, registrationRepository);
  }

  private EventEntity event() {
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            STARTS,
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

  private UpdateEventCommand command(Long userId, List<QuestionSpec> questions) {
    return new UpdateEventCommand(
        userId,
        10L,
        "새 제목",
        "설명",
        STARTS.plusSeconds(3600),
        STARTS.plusSeconds(7200),
        "Asia/Tokyo",
        "장소",
        "https://map.example",
        "https://meet.example",
        20,
        STARTS,
        questions);
  }

  @Test
  void missingEvent_isNotFound() {
    when(eventRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(command(1L, null)))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
  }

  @Test
  void otherOwnersEvent_isDenied() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));

    assertThatThrownBy(() -> useCase.execute(command(99L, null)))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_PERMISSION_DENIED);
  }

  @Test
  void nullQuestions_updatesFieldsAndLeavesQuestionsAlone() {
    EventEntity event = event();
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

    EventEntity updated = useCase.execute(command(1L, null));

    assertThat(updated.getTitle()).isEqualTo("새 제목");
    assertThat(updated.getTimezone()).isEqualTo("Asia/Tokyo");
    assertThat(updated.getCapacity()).isEqualTo(20);
    verify(questionRepository, never()).deleteAllByEventId(10L);
  }

  @Test
  void canceledEvent_rejectsUpdate() {
    EventEntity event = event();
    event.cancel();
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

    assertThatThrownBy(() -> useCase.execute(command(1L, null)))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.EVENT_CANCELED);
  }

  @Test
  void questionChange_isLockedOnceRegistrationsExist() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
    when(registrationRepository.countConfirmedByEventId(10L)).thenReturn(1L);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    command(1L, List.of(new QuestionSpec("SHORT_TEXT", "이름", null, true)))))
        .isInstanceOf(EventException.class)
        .extracting(e -> ((EventException) e).errorCode())
        .isEqualTo(EventErrorCode.INVALID_QUESTIONS);
    verify(questionRepository, never()).deleteAllByEventId(10L);
  }

  @Test
  void emptyQuestionList_wipesWithoutReinsert() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
    when(registrationRepository.countConfirmedByEventId(10L)).thenReturn(0L);

    useCase.execute(command(1L, List.of()));

    verify(questionRepository).deleteAllByEventId(10L);
    verify(questionRepository, never()).saveAll(List.of());
  }

  @Test
  void questionSpecs_areReplacedInOrder() {
    when(eventRepository.findById(10L)).thenReturn(Optional.of(event()));
    when(registrationRepository.countConfirmedByEventId(10L)).thenReturn(0L);

    useCase.execute(
        command(
            1L,
            List.of(
                new QuestionSpec("SHORT_TEXT", "  한 줄 소개  ", null, true),
                new QuestionSpec("SINGLE_CHOICE", "티셔츠", List.of("S", "M"), false))));

    verify(questionRepository).deleteAllByEventId(10L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EventQuestionEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(questionRepository).saveAll(captor.capture());
    List<EventQuestionEntity> saved = captor.getValue();
    assertThat(saved).hasSize(2);
    assertThat(saved.get(0).getLabel()).isEqualTo("한 줄 소개");
    assertThat(saved.get(0).getPosition()).isZero();
    assertThat(saved.get(1).getOptionsJson()).contains("\"S\"", "\"M\"");
  }
}
