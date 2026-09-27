package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventLinkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaEventLinkRepository extends JpaRepository<EventLinkEntity, Long> {

  List<EventLinkEntity> findAllByEventId(Long eventId);

  Optional<EventLinkEntity> findByLinkId(Long linkId);
}
