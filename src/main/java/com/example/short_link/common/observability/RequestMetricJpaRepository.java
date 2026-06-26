package com.example.short_link.common.observability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RequestMetricJpaRepository extends JpaRepository<RequestMetricEntity, Long> {

  /** Drop the user link on account hard delete — keeps the operational row, removes the PII. */
  @Modifying
  @Transactional
  @Query("UPDATE RequestMetricEntity m SET m.userId = null WHERE m.userId = :userId")
  int anonymizeUser(@Param("userId") Long userId);

  /** Sliding retention window — request logs are operational telemetry, not a permanent record. */
  @Modifying
  @Transactional
  @Query("DELETE FROM RequestMetricEntity m WHERE m.occurredAt < :cutoff")
  int deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);

  @Query(
      "SELECT m FROM RequestMetricEntity m "
          + "WHERE m.occurredAt >= :from AND m.occurredAt < :to "
          + "ORDER BY m.occurredAt ASC")
  List<RequestMetricEntity> findWindow(@Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "SELECT m FROM RequestMetricEntity m "
          + "WHERE m.shortCode = :shortCode AND m.occurredAt >= :from AND m.occurredAt < :to "
          + "ORDER BY m.occurredAt ASC")
  List<RequestMetricEntity> findShortCodeWindow(
      @Param("shortCode") String shortCode, @Param("from") Instant from, @Param("to") Instant to);
}
