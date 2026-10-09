package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import com.example.short_link.notification.domain.policy.FilteredSender;
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
                    + " FROM notification WHERE recipient_user_id = :recipient AND NOT filtered"
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
                    + " WHERE recipient_user_id = :recipient AND group_key IN (:keys) AND NOT filtered"
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
                        + " WHERE recipient_user_id = :recipient AND read_at IS NULL AND NOT filtered")
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
                + " AND (n.id = target.id OR (n.group_key = target.group_key AND NOT n.filtered))")
        .setParameter("id", id)
        .setParameter("recipient", recipientUserId)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public int markAllRead(Long recipientUserId, Instant at) {
    return jpa.markAllRead(recipientUserId, at);
  }

  @Override
  public int deleteFromServer(String domain) {
    return em.createNativeQuery(
            "DELETE n FROM notification n JOIN federation_remote_actor a ON a.id = n.actor_remote_id"
                + " WHERE a.domain = :domain OR a.domain LIKE :subdomains")
        .setParameter("domain", domain)
        .setParameter("subdomains", "%." + domain)
        .executeUpdate();
  }

  @Override
  public int deleteFollowRequest(Long recipientUserId, Long actorUserId, Long actorRemoteId) {
    return em.createNativeQuery(
            "DELETE FROM notification WHERE recipient_user_id = :recipient"
                + " AND type = 'FOLLOW_REQUEST' AND actor_user_id <=> :actorUserId"
                + " AND actor_remote_id <=> :actorRemoteId")
        .setParameter("recipient", recipientUserId)
        .setParameter("actorUserId", actorUserId)
        .setParameter("actorRemoteId", actorRemoteId)
        .executeUpdate();
  }

  // The filtered inbox: one row per sender, newest first, with how many notices wait.
  @Override
  public List<FilteredSender> filteredSenders(Long recipientUserId, int limit) {
    return em.createQuery(
            "select new com.example.short_link.notification.domain.policy.FilteredSender("
                + "n.actorUserId, n.actorRemoteId, count(n), max(n.createdAt))"
                + " from NotificationEntity n where n.recipientUserId = :recipient and n.filtered = true"
                + " and (n.actorUserId is not null or n.actorRemoteId is not null)"
                + " group by n.actorUserId, n.actorRemoteId order by max(n.id) desc",
            FilteredSender.class)
        .setParameter("recipient", recipientUserId)
        .setMaxResults(limit)
        .getResultList();
  }

  @Override
  public int unfilter(Long recipientUserId, Long actorUserId, Long actorRemoteId) {
    return em.createNativeQuery(
            "UPDATE notification SET filtered = FALSE WHERE recipient_user_id = :recipient"
                + " AND filtered AND actor_user_id <=> :actorUserId"
                + " AND actor_remote_id <=> :actorRemoteId")
        .setParameter("recipient", recipientUserId)
        .setParameter("actorUserId", actorUserId)
        .setParameter("actorRemoteId", actorRemoteId)
        .executeUpdate();
  }

  @Override
  public int deleteFiltered(Long recipientUserId, Long actorUserId, Long actorRemoteId) {
    return em.createNativeQuery(
            "DELETE FROM notification WHERE recipient_user_id = :recipient"
                + " AND filtered AND actor_user_id <=> :actorUserId"
                + " AND actor_remote_id <=> :actorRemoteId")
        .setParameter("recipient", recipientUserId)
        .setParameter("actorUserId", actorUserId)
        .setParameter("actorRemoteId", actorRemoteId)
        .executeUpdate();
  }

  @Override
  public int deleteFollowRequests(Long recipientUserId) {
    return em.createNativeQuery(
            "DELETE FROM notification WHERE recipient_user_id = :recipient"
                + " AND type = 'FOLLOW_REQUEST'")
        .setParameter("recipient", recipientUserId)
        .executeUpdate();
  }

  @Override
  public int deleteFromDomain(Long recipientUserId, String domain) {
    return em.createNativeQuery(
            "DELETE n FROM notification n JOIN federation_remote_actor a ON a.id = n.actor_remote_id"
                + " WHERE n.recipient_user_id = :recipient AND a.domain = :domain")
        .setParameter("recipient", recipientUserId)
        .setParameter("domain", domain)
        .executeUpdate();
  }
}
