package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.FederationDeliveryEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FederationDeliveryRepository {

  boolean existsByDedupeKey(String dedupeKey);

  FederationDeliveryEntity saveAndFlush(FederationDeliveryEntity delivery);

  Optional<FederationDeliveryEntity> findById(Long id);

  // Locks due rows with SKIP LOCKED so two workers (e.g. old and new container during a deploy)
  // never take the same delivery.
  List<FederationDeliveryEntity> lockDue(Instant now, int limit);

  int deleteFinishedBefore(Instant cutoff);
}
