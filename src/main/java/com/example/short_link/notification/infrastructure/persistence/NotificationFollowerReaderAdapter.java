package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.repository.NotificationFollowerReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

// Uses a native query to avoid an entity dependency on the user module.
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

  // The time is bound from Java so a mute's end is compared in UTC.
  @Override
  public List<Long> noteSubscribersOf(Long authorUserId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT f.follower_id FROM user_follow f WHERE f.following_id = :id"
                    + " AND f.notify_notes"
                    + " AND NOT EXISTS (SELECT 1 FROM user_block b"
                    + " WHERE b.blocker_id = f.follower_id AND b.blocked_id = :id)"
                    + " AND NOT EXISTS (SELECT 1 FROM user_mute m"
                    + " WHERE m.user_id = f.follower_id AND m.muted_user_id = :id"
                    + " AND m.hide_notifications AND (m.expires_at IS NULL OR m.expires_at > :now))")
            .setParameter("id", authorUserId)
            .setParameter("now", Instant.now())
            .getResultList();
    return rows.stream().map(raw -> ((Number) raw).longValue()).toList();
  }
}
