package com.example.short_link.link.health.domain.repository;

import com.example.short_link.link.health.domain.LinkDestinationHealthEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LinkDestinationHealthRepository {

  Optional<LinkDestinationHealthEntity> findById(Long linkId);

  LinkDestinationHealthEntity save(LinkDestinationHealthEntity health);

  List<DueDestination> findDue(Instant now, Instant checkedBefore, int limit);

  record DueDestination(
      Long linkId, String shortCode, Long userId, String originalUrl, String note) {}
}
