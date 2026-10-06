package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// A notification without a group key is its own group, keyed by its id so it never merges.
@Repository
@RequiredArgsConstructor
class NotificationRepositoryAdapter implements NotificationRepository {

  private static final String GROUP = "COALESCE(group_key, CONCAT('#', id))";

  private final JpaNotificationRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public NotificationEntity save(NotificationEntity notification) {
    return jpa.save(notification);
  }

  // A group sits where its newest member is, so the cursor is that member's id and a group never
  // repeats on a later page even when its older members are below the cursor. The newest member
  // comes back as an entity alongside the group's size, in the same query.
  @Override
  public List<NotificationGroup> findGroupPage(Long recipientUserId, Long beforeId, int limit) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT n.*, g.members, g.unread FROM ("
                    + "SELECT MAX(id) AS last_id, COUNT(*) AS members,"
                    + " SUM(read_at IS NULL) AS unread"
                    + " FROM notification WHERE recipient_user_id = :recipient"
                    + " GROUP BY "
                    + GROUP
                    + ") g JOIN notification n ON n.id = g.last_id"
                    + " WHERE g.last_id < :before ORDER BY g.last_id DESC LIMIT :limit",
                NotificationEntity.GROUP_MAPPING)
            .setParameter("recipient", recipientUserId)
            .setParameter("before", beforeId == null ? Long.MAX_VALUE : beforeId)
            .setParameter("limit", limit)
            .getResultList();
    List<NotificationGroup> groups = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      groups.add(
          new NotificationGroup(
              (NotificationEntity) cols[0],
              ((Number) cols[1]).longValue(),
              ((Number) cols[2]).longValue() > 0));
    }
    return groups;
  }

  @Override
  public List<NotificationGroupActor> recentActors(
      Long recipientUserId, Collection<String> groupKeys, int perGroup) {
    if (groupKeys.isEmpty()) {
      return List.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT t.group_key, t.actor_user_id, t.actor_remote_id FROM ("
                    + "SELECT id, group_key, actor_user_id, actor_remote_id,"
                    + " ROW_NUMBER() OVER (PARTITION BY group_key ORDER BY id DESC) AS position"
                    + " FROM notification"
                    + " WHERE recipient_user_id = :recipient AND group_key IN (:keys)"
                    + ") t WHERE t.position <= :perGroup ORDER BY t.id DESC")
            .setParameter("recipient", recipientUserId)
            .setParameter("keys", groupKeys)
            .setParameter("perGroup", perGroup)
            .getResultList();
    List<NotificationGroupActor> actors = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] cols = (Object[]) raw;
      actors.add(
          new NotificationGroupActor(
              cols[0].toString(),
              cols[1] == null ? null : ((Number) cols[1]).longValue(),
              cols[2] == null ? null : ((Number) cols[2]).longValue()));
    }
    return actors;
  }

  @Override
  public boolean existsInGroup(
      Long recipientUserId, String groupKey, Long actorUserId, Long actorRemoteId) {
    Number found =
        (Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM notification"
                        + " WHERE recipient_user_id = :recipient AND group_key = :groupKey"
                        + " AND actor_user_id <=> :actorUserId"
                        + " AND actor_remote_id <=> :actorRemoteId")
                .setParameter("recipient", recipientUserId)
                .setParameter("groupKey", groupKey)
                .setParameter("actorUserId", actorUserId)
                .setParameter("actorRemoteId", actorRemoteId)
                .getSingleResult();
    return found.longValue() > 0;
  }

  @Override
  public long countUnread(Long recipientUserId) {
    Number unread =
        (Number)
            em.createNativeQuery(
                    "SELECT COUNT(DISTINCT "
                        + GROUP
                        + ") FROM notification"
                        + " WHERE recipient_user_id = :recipient AND read_at IS NULL")
                .setParameter("recipient", recipientUserId)
                .getSingleResult();
    return unread.longValue();
  }

  // Reading a group's newest member reads the whole group, which the list shows as one row.
  @Override
  public void markRead(Long id, Long recipientUserId, Instant at) {
    em.createNativeQuery(
            "UPDATE notification n JOIN notification target"
                + " ON target.id = :id AND target.recipient_user_id = :recipient"
                + " SET n.read_at = :at"
                + " WHERE n.recipient_user_id = :recipient AND n.read_at IS NULL"
                + " AND (n.id = target.id OR n.group_key = target.group_key)")
        .setParameter("id", id)
        .setParameter("recipient", recipientUserId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public int markAllRead(Long recipientUserId, Instant at) {
    return jpa.markAllRead(recipientUserId, at);
  }
}
