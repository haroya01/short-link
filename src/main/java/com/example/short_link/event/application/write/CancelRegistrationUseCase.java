package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.CancelTokens;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelRegistrationUseCase {

  private final EventRegistrationRepository registrationRepository;
  private final EventRepository eventRepository;

  @Transactional
  public void execute(String cancelToken) {
    if (cancelToken == null || cancelToken.isBlank()) {
      throw new EventException(EventErrorCode.REGISTRATION_NOT_FOUND);
    }
    EventRegistrationEntity registration =
        registrationRepository
            .findByCancelTokenHash(CancelTokens.hash(cancelToken))
            .orElseThrow(() -> new EventException(EventErrorCode.REGISTRATION_NOT_FOUND));
    if (!registration.isConfirmed()) {
      return;
    }
    registration.cancel(Instant.now());
    eventRepository.decrementRegistrationCount(registration.getEventId());
  }
}
