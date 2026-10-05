package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.DeliveryStatus;
import com.example.short_link.federation.domain.FederationDeliveryEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface JpaFederationDeliveryRepository
    extends JpaRepository<FederationDeliveryEntity, Long> {

  boolean existsByDedupeKey(String dedupeKey);

  // lock.timeout -2 is Hibernate's SKIP LOCKED.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      SELECT d FROM FederationDeliveryEntity d
      WHERE d.status = :status AND d.nextAttemptAt <= :now
      ORDER BY d.nextAttemptAt ASC, d.id ASC
      """)
  List<FederationDeliveryEntity> lockDue(
      @Param("status") DeliveryStatus status, @Param("now") Instant now, Pageable page);

  @Modifying
  @Query(
      """
      DELETE FROM FederationDeliveryEntity d
      WHERE d.status <> :pending AND d.updatedAt < :cutoff
      """)
  int deleteFinishedBefore(
      @Param("pending") DeliveryStatus pending, @Param("cutoff") Instant cutoff);
}
