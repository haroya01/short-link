package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.federation.domain.RemoteAccountStanding;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class RemoteAccountStandingReaderAdapter implements RemoteAccountStandingReader {

  private static final String STANDING =
      "SELECT a.id, EXISTS (SELECT 1 FROM user_domain_block d"
          + " WHERE d.user_id = :viewer AND d.domain = a.domain), "
          + HeardSql.remoteBlocked(":viewer", "a.id")
          + ", "
          + HeardSql.remoteMuted(":viewer", "a.id")
          + " FROM federation_remote_actor a WHERE a.id IN (:ids)";

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, RemoteAccountStanding> of(
      Long userId, Collection<Long> remoteActorIds, Instant now) {
    Map<Long, RemoteAccountStanding> standings = new HashMap<>();
    if (userId == null || remoteActorIds.isEmpty()) {
      return standings;
    }
    List<Object[]> rows =
        em.createNativeQuery(STANDING)
            .setParameter("viewer", userId)
            .setParameter("ids", remoteActorIds)
            .setParameter("now", now)
            .getResultList();
    for (Object[] row : rows) {
      standings.put(
          ((Number) row[0]).longValue(),
          new RemoteAccountStanding(flag(row[1]), flag(row[2]), flag(row[3])));
    }
    return standings;
  }

  private static boolean flag(Object value) {
    return value instanceof Number n ? n.intValue() != 0 : Boolean.TRUE.equals(value);
  }
}
