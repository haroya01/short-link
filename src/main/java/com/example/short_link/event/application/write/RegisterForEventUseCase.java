package com.example.short_link.event.application.write;

import com.example.short_link.event.application.RegistrationAttributor;
import com.example.short_link.event.application.helper.CancelTokens;
import com.example.short_link.event.application.helper.EventContacts;
import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
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

/** 정원을 원자적으로 확보한 뒤 저장한다. 연락처 중복 경합으로 저장에 실패하면 확보한 정원을 돌려준다. 취소된 연락처의 재신청은 기존 행을 복구한다. */
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
    event.requireRegistrationOpen(Instant.now());
    String contact = EventContacts.normalize(event.getContactField(), cmd.contact());
    String name = EventRegistrationEntity.normalizeName(cmd.name());
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
}
