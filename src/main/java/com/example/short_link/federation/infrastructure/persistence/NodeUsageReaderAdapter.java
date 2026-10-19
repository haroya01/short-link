package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.federation.domain.NodeUsage;
import com.example.short_link.federation.domain.repository.NodeUsageReader;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NodeUsageReaderAdapter implements NodeUsageReader {

  private static final String USAGE =
      "SELECT (SELECT COUNT(*) FROM federation_actor fa JOIN users u ON u.id = fa.user_id"
          + " WHERE u.deleted_at IS NULL AND u.username IS NOT NULL"
          + " AND u.moderation_status <> 'BANNED'"
          + " AND NOT (u.moderation_status = 'SUSPENDED' AND u.suspended_until > :now)"
          + " AND NOT EXISTS (SELECT 1 FROM federation_preference p"
          + " WHERE p.user_id = u.id AND NOT p.enabled)),"
          + " (SELECT COUNT(*) FROM note n JOIN users nu ON nu.id = n.user_id"
          + " WHERE "
          + NoteAccessSql.listed("n")
          + " AND nu.deleted_at IS NULL)";

  private final EntityManager em;

  @Override
  public NodeUsage read(Instant now) {
    Object[] row =
        (Object[]) em.createNativeQuery(USAGE).setParameter("now", now).getSingleResult();
    return new NodeUsage(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
  }
}
