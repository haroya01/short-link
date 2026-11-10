package com.example.short_link.common.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM AuditLogEntity a WHERE a.occurredAt < :cutoff")
  int deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);

  @Query(
      value =
          "SELECT a.action AS action, COUNT(*) AS changes, MAX(a.id) AS latestId "
              + "FROM audit_log a "
              + "WHERE a.target_type = :targetType AND a.target_id = :targetId "
              + "AND a.action IN (:actions) AND a.occurred_at >= :since "
              + "AND JSON_EXTRACT(a.metadata, '$.urlChanged') = CAST('true' AS JSON) "
              + "GROUP BY a.action",
      nativeQuery = true)
  List<AddressChangeCount> countAddressChanges(
      @Param("targetType") String targetType,
      @Param("targetId") String targetId,
      @Param("actions") Collection<String> actions,
      @Param("since") Instant since);

  interface AddressChangeCount {
    String getAction();

    Long getChanges();

    Long getLatestId();
  }
}
