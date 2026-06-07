package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Reads an author's follower ids via one native query against the user module's {@code user_follow}
 * table — a native query so the notification module needn't depend on the user module's entity.
 */
@Repository
class NotificationFollowerReaderAdapter implements NotificationFollowerReader {

  @PersistenceContext private EntityManager em;

  @Override
  public List<Long> followerIdsOf(Long authorUserId) {
    if (authorUserId == null) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery("SELECT follower_id FROM user_follow WHERE following_id = :id")
            .setParameter("id", authorUserId)
            .getResultList();
    return rows.stream().map(raw -> ((Number) raw).longValue()).toList();
  }
}
