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
import java.util.ArrayList;
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
    replaceQuestions(event, cmd.questions());
    return event;
  }

  private void replaceQuestions(EventEntity event, List<QuestionSpec> specs) {
    if (specs == null) return;
    if (registrationRepository.countConfirmedByEventId(event.getId()) > 0) {
      // 답변이 이미 달린 질문 구조를 바꾸면 answers 매핑이 깨진다 — 신청자가 있으면 잠근다.
      throw new EventException(EventErrorCode.INVALID_QUESTIONS, "registrations exist");
    }
    EventQuestions.validateSpecs(specs);
    questionRepository.deleteAllByEventId(event.getId());
    if (specs.isEmpty()) return;
    List<EventQuestionEntity> questions = new ArrayList<>();
    for (int i = 0; i < specs.size(); i++) {
      QuestionSpec spec = specs.get(i);
      questions.add(
          new EventQuestionEntity(
              event.getId(),
              i,
              EventQuestions.parseType(spec.type()),
              spec.label().trim(),
              EventQuestions.serializeOptions(spec.options()),
              spec.required()));
    }
    questionRepository.saveAll(questions);
  }
}
