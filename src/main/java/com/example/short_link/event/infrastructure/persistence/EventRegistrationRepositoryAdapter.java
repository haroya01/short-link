package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.RegistrationStatus;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class EventRegistrationRepositoryAdapter implements EventRegistrationRepository {

  private final JpaEventRegistrationRepository jpa;

  @Override
  public EventRegistrationEntity save(EventRegistrationEntity registration) {
    return jpa.save(registration);
  }

  @Override
  public Optional<EventRegistrationEntity> findByEventIdAndContact(Long eventId, String contact) {
    return jpa.findByEventIdAndContact(eventId, contact);
  }

  @Override
  public Optional<EventRegistrationEntity> findByCancelTokenHash(String cancelTokenHash) {
    return jpa.findByCancelTokenHash(cancelTokenHash);
  }

  @Override
  public List<EventRegistrationEntity> findAllByEventIdOrderByCreatedAtAsc(Long eventId) {
    return jpa.findAllByEventIdOrderByCreatedAtAsc(eventId);
  }

  @Override
  public long countConfirmedByEventId(Long eventId) {
    return jpa.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED);
  }

  @Override
  public int purgePiiByEventId(Long eventId) {
    return jpa.purgePiiByEventId(eventId);
  }
}
