package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventLinkEntity;
import java.util.List;
import java.util.Optional;

public interface EventLinkRepository {

  EventLinkEntity save(EventLinkEntity eventLink);

  List<EventLinkEntity> findAllByEventId(Long eventId);

  Optional<EventLinkEntity> findByLinkId(Long linkId);
}
