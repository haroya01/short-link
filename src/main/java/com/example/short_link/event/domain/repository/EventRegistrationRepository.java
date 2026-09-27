package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventRegistrationEntity;
import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository {

  EventRegistrationEntity save(EventRegistrationEntity registration);

  Optional<EventRegistrationEntity> findByEventIdAndContact(Long eventId, String contact);

  Optional<EventRegistrationEntity> findByCancelTokenHash(String cancelTokenHash);

  List<EventRegistrationEntity> findAllByEventIdOrderByCreatedAtAsc(Long eventId);

  long countConfirmedByEventId(Long eventId);

  int purgePiiByEventId(Long eventId);
}
