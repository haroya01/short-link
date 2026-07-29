package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventLinkEntity;
import java.util.List;

public interface EventLinkRepository {

  EventLinkEntity save(EventLinkEntity eventLink);

  List<EventLinkEntity> findAllByEventId(Long eventId);
}
