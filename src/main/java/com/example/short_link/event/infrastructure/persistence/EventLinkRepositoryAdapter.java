package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class EventLinkRepositoryAdapter implements EventLinkRepository {

  private final JpaEventLinkRepository jpa;

  @Override
  public EventLinkEntity save(EventLinkEntity eventLink) {
    return jpa.save(eventLink);
  }

  @Override
  public List<EventLinkEntity> findAllByEventId(Long eventId) {
    return jpa.findAllByEventId(eventId);
  }
}
