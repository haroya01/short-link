package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.application.helper.EventSlugs;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CreateEventUseCase {

  private static final int MAX_SLUG_ATTEMPTS = 5;
  public static final String PRIMARY_LINK_LABEL = "기본";

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventLinkIssuer linkIssuer;
  private final TransactionTemplate transaction;

  public EventEntity execute(CreateEventCommand cmd) {
    EventQuestions.validateSpecs(cmd.questions());
    EventEntity event = transaction.execute(status -> save(cmd));
    linkIssuer
        .issue(cmd.userId(), event.getId(), event.getSlug(), PRIMARY_LINK_LABEL)
        .ifPresent(issued -> attachPrimaryLink(event, issued.linkId()));
    return event;
  }

  private EventEntity save(CreateEventCommand cmd) {
    EventEntity event = saveWithFreshSlug(cmd);
    saveQuestions(event.getId(), cmd.questions());
    return event;
  }

  private void attachPrimaryLink(EventEntity event, Long linkId) {
    transaction.executeWithoutResult(
        status -> eventRepository.attachPrimaryLink(event.getId(), linkId));
    event.attachPrimaryLink(linkId);
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
    questionRepository.saveAll(EventQuestions.toEntities(eventId, specs));
  }
}
