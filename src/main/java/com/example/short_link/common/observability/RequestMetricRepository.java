package com.example.short_link.common.observability;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestMetricRepository extends JpaRepository<RequestMetricEntity, Long> {

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

  long deleteByOccurredAtBefore(Instant cutoff);
}
