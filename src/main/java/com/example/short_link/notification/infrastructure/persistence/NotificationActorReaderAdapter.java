package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Resolves notification actors via one native batch read against the user module's {@code users}
 * table — a native query so the notification module needn't depend on the user module's entity.
 */
@Repository
class NotificationActorReaderAdapter implements NotificationActorReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, NotificationActor> resolve(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    List<?> rows =
        em.createNativeQuery("SELECT id, username, avatar_url FROM users WHERE id IN (:ids)")
            .setParameter("ids", userIds)
            .getResultList();
    Map<Long, NotificationActor> resolved = new HashMap<>();
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      Long id = ((Number) cols[0]).longValue();
      String username = cols[1] == null ? null : cols[1].toString();
      String avatarUrl = cols[2] == null ? null : cols[2].toString();
      resolved.put(id, new NotificationActor(id, username, avatarUrl));
    }
    return resolved;
  }
}
