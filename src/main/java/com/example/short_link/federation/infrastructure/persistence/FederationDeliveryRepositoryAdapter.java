package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.DeliveryStatus;
import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationDeliveryRepositoryAdapter implements FederationDeliveryRepository {

  private final JpaFederationDeliveryRepository jpa;

  @Override
  public boolean existsByDedupeKey(String dedupeKey) {
    return jpa.existsByDedupeKey(dedupeKey);
  }

  @Override
  public FederationDeliveryEntity saveAndFlush(FederationDeliveryEntity delivery) {
    return jpa.saveAndFlush(delivery);
  }

  @Override
  public Optional<FederationDeliveryEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public List<FederationDeliveryEntity> lockDue(Instant now, int limit) {
    return jpa.lockDue(DeliveryStatus.PENDING, now, PageRequest.of(0, limit));
  }

  @Override
  public int deleteFinishedBefore(Instant cutoff) {
    return jpa.deleteFinishedBefore(DeliveryStatus.PENDING, cutoff);
  }
}
