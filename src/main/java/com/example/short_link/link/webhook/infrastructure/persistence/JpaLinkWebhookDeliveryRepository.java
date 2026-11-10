package com.example.short_link.link.webhook.infrastructure.persistence;

import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryStatus;
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

public interface JpaLinkWebhookDeliveryRepository
    extends JpaRepository<LinkWebhookDeliveryEntity, Long> {

  // lock.timeout -2 is Hibernate's SKIP LOCKED.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      SELECT d FROM LinkWebhookDeliveryEntity d
      WHERE d.status = :status AND d.nextAttemptAt <= :now
      ORDER BY d.nextAttemptAt ASC, d.id ASC
      """)
  List<LinkWebhookDeliveryEntity> lockDue(
      @Param("status") WebhookDeliveryStatus status, @Param("now") Instant now, Pageable page);

  @Modifying
  @Query(
      """
      DELETE FROM LinkWebhookDeliveryEntity d
      WHERE d.status <> :pending AND d.updatedAt < :cutoff
      """)
  int deleteFinishedBefore(
      @Param("pending") WebhookDeliveryStatus pending, @Param("cutoff") Instant cutoff);
}
