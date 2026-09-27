package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateEventUseCase {

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventRegistrationRepository registrationRepository;

  @Transactional
  public EventEntity execute(UpdateEventCommand cmd) {
    EventEntity event =
        eventRepository
            .findById(cmd.eventId())
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, cmd.eventId()));
    if (!event.isOwnedBy(cmd.userId())) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    event.requireEditable();
    List<EventQuestionEntity> questions = prepareQuestions(event, cmd.questions());
    event.update(
        cmd.title(),
        cmd.descriptionMd(),
        cmd.startsAt(),
        cmd.endsAt(),
        cmd.timezone(),
        cmd.locationText(),
        cmd.locationUrl(),
        cmd.onlineUrl(),
        cmd.capacity(),
        cmd.closeAt());
    if (questions != null) {
      questionRepository.deleteAllByEventId(event.getId());
      if (!questions.isEmpty()) {
        questionRepository.saveAll(questions);
      }
    }
    return event;
  }

  private List<EventQuestionEntity> prepareQuestions(EventEntity event, List<QuestionSpec> specs) {
    if (specs == null) return null;
    event.requireQuestionChangesAllowed(
        registrationRepository.countConfirmedByEventId(event.getId()));
    EventQuestions.validateSpecs(specs);
    return EventQuestions.toEntities(event.getId(), specs);
  }
}
