package com.example.short_link.event.application.write;

import com.example.short_link.event.application.RegistrationAttributor;
import com.example.short_link.event.application.helper.CancelTokens;
import com.example.short_link.event.application.helper.EventContacts;
import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.EventStatus;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비로그인 신청. 순서가 정합성의 전부다: 검증 → 정원 원자 증가(만석 판정) → 저장. 저장이 UNIQUE(event_id, contact) 경합으로 지면 증가분을
 * 되돌린다. 취소된 같은 contact 의 재신청은 그 행을 되살린다.
 */
@Service
@RequiredArgsConstructor
public class RegisterForEventUseCase {

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventRegistrationRepository registrationRepository;
  private final RegistrationAttributor attributor;
  private final RegistrationMailer mailer;
  private final MeterRegistry meterRegistry;

  @Transactional
  public RegistrationResult execute(RegisterForEventCommand cmd) {
    EventEntity event =
        eventRepository
            .findBySlug(cmd.slug())
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, cmd.slug()));
    requireOpen(event);
    String contact = EventContacts.normalize(event.getContactField(), cmd.contact());
    String name = requireName(cmd.name());
    String answersJson =
        EventQuestions.validateAndSerializeAnswers(
            questionRepository.findAllByEventIdOrderByPosition(event.getId()), cmd.answers());

    Optional<EventRegistrationEntity> existing =
        registrationRepository.findByEventIdAndContact(event.getId(), contact);
    if (existing.isPresent() && existing.get().isConfirmed()) {
      throw new EventException(EventErrorCode.ALREADY_REGISTERED);
    }
    if (eventRepository.tryIncrementRegistrationCount(event.getId()) == 0) {
      meterRegistry.counter("event.registration", "result", "full").increment();
      throw new EventException(EventErrorCode.EVENT_FULL, event.getId());
    }

    String cancelToken = CancelTokens.generate();
    EventRegistrationEntity registration;
    try {
      if (existing.isPresent()) {
        registration = existing.get();
        registration.reactivate(name, answersJson, CancelTokens.hash(cancelToken));
      } else {
        registration =
            new EventRegistrationEntity(
                event.getId(), name, contact, answersJson, CancelTokens.hash(cancelToken));
      }
      registration = registrationRepository.save(registration);
    } catch (DataIntegrityViolationException e) {
      eventRepository.decrementRegistrationCount(event.getId());
      throw new EventException(EventErrorCode.ALREADY_REGISTERED);
    }

    attributor.attribute(event.getId(), registration, cmd.clientIp(), cmd.userAgent());
    mailer.sendConfirmation(event, registration, cancelToken);
    meterRegistry.counter("event.registration", "result", "ok").increment();

    Integer spotsLeft =
        event.getCapacity() == null
            ? null
            : Math.max(0, event.getCapacity() - (event.getRegistrationCount() + 1));
    return new RegistrationResult(registration.getId(), cancelToken, spotsLeft);
  }

  private void requireOpen(EventEntity event) {
    if (event.getStatus() == EventStatus.CANCELED) {
      throw new EventException(EventErrorCode.EVENT_CANCELED, event.getId());
    }
    if (event.getStatus() != EventStatus.OPEN
        || (event.getCloseAt() != null && !Instant.now().isBefore(event.getCloseAt()))) {
      throw new EventException(EventErrorCode.EVENT_REGISTRATION_CLOSED, event.getId());
    }
  }

  private static String requireName(String raw) {
    if (raw == null || raw.isBlank() || raw.trim().length() > 100) {
      throw new EventException(EventErrorCode.INVALID_ANSWER, "name");
    }
    return raw.trim();
  }
}
