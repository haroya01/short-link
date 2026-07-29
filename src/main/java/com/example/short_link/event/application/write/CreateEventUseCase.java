package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.application.helper.EventSlugs;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateEventUseCase {

  private static final int MAX_SLUG_ATTEMPTS = 5;
  public static final String PRIMARY_LINK_LABEL = "기본";

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventLinkIssuer linkIssuer;

  @Transactional
  public EventEntity execute(CreateEventCommand cmd) {
    EventQuestions.validateSpecs(cmd.questions());
    EventEntity event = saveWithFreshSlug(cmd);
    saveQuestions(event.getId(), cmd.questions());
    linkIssuer
        .issue(cmd.userId(), event.getId(), event.getSlug(), PRIMARY_LINK_LABEL)
        .ifPresent(issued -> event.attachPrimaryLink(issued.linkId()));
    return event;
  }

  private EventEntity saveWithFreshSlug(CreateEventCommand cmd) {
    for (int i = 0; i < MAX_SLUG_ATTEMPTS; i++) {
      try {
        return eventRepository.save(
            new EventEntity(
                cmd.userId(),
                EventSlugs.generate(),
                cmd.title(),
                cmd.descriptionMd(),
                cmd.startsAt(),
                cmd.endsAt(),
                cmd.timezone(),
                cmd.locationText(),
                cmd.locationUrl(),
                cmd.onlineUrl(),
                cmd.capacity(),
                cmd.closeAt(),
                cmd.contactField()));
      } catch (DataIntegrityViolationException ignored) {
      }
    }
    throw new EventException(EventErrorCode.SLUG_EXHAUSTED);
  }

  private void saveQuestions(Long eventId, List<QuestionSpec> specs) {
    if (specs == null || specs.isEmpty()) return;
    List<EventQuestionEntity> questions = new ArrayList<>();
    for (int i = 0; i < specs.size(); i++) {
      QuestionSpec spec = specs.get(i);
      questions.add(
          new EventQuestionEntity(
              eventId,
              i,
              EventQuestions.parseType(spec.type()),
              spec.label().trim(),
              EventQuestions.serializeOptions(spec.options()),
              spec.required()));
    }
    questionRepository.saveAll(questions);
  }
}
