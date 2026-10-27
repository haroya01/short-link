package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.RegistrationStatus;
import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository {

  EventRegistrationEntity save(EventRegistrationEntity registration);

  Optional<EventRegistrationEntity> findByEventIdAndContact(Long eventId, String contact);

  Optional<EventRegistrationEntity> findByCancelTokenHash(String cancelTokenHash);

  List<EventRegistrationEntity> findAllByEventIdOrderByCreatedAtAsc(Long eventId);

  long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

  int purgePiiByEventId(Long eventId);
}
