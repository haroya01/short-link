package com.example.short_link.common.audit;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AuditLogEntity a WHERE a.occurredAt < :cutoff")
  int deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);
}
