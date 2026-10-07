package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.policy.NotificationPolicy;
import com.example.short_link.notification.domain.policy.NotificationPolicyLevel;
import com.example.short_link.notification.domain.policy.NotificationSender;
import com.example.short_link.notification.domain.repository.NotificationPolicyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Mastodon's thresholds: an account is new for thirty days, and a follower counts once they have
// followed for three.
@Repository
class NotificationPolicyRepositoryAdapter implements NotificationPolicyRepository {

  static final Duration NEW_ACCOUNT = Duration.ofDays(30);
  static final Duration SETTLED_FOLLOWER = Duration.ofDays(3);

  private static final String POLICY_COLUMNS =
      "for_not_following, for_not_followers, for_new_accounts, for_private_mentions";

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<NotificationPolicy> find(Long userId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT " + POLICY_COLUMNS + " FROM notification_policy WHERE user_id = :user")
            .setParameter("user", userId)
            .getResultList();
    return rows.stream().findFirst().map(row -> policy((Object[]) row, 0));
  }

  @Override
  public void save(Long userId, NotificationPolicy policy) {
    em.createNativeQuery(
            "INSERT INTO notification_policy (user_id, "
                + POLICY_COLUMNS
                + ", updated_at) VALUES (:user, :notFollowing, :notFollowers, :newAccounts,"
                + " :privateMentions, :now) ON DUPLICATE KEY UPDATE"
                + " for_not_following = :notFollowing, for_not_followers = :notFollowers,"
                + " for_new_accounts = :newAccounts, for_private_mentions = :privateMentions,"
                + " updated_at = :now")
        .setParameter("user", userId)
        .setParameter("notFollowing", policy.forNotFollowing().name())
        .setParameter("notFollowers", policy.forNotFollowers().name())
        .setParameter("newAccounts", policy.forNewAccounts().name())
        .setParameter("privateMentions", policy.forPrivateMentions().name())
        .setParameter("now", Instant.now())
        .executeUpdate();
  }

  @Override
  public NotificationSender sender(
      Long recipientUserId,
      NotificationType type,
      Long actorUserId,
      Long actorRemoteId,
      Long mentionNoteId) {
    Instant now = Instant.now();
    Object[] row =
        (Object[])
            em.createNativeQuery(
                    "SELECT "
                        + POLICY_COLUMNS
                        + ", COALESCE((SELECT pr.enabled FROM blog_notification_preference pr"
                        + " WHERE pr.user_id = :recipient AND pr.type = :type), TRUE),"
                        + " EXISTS (SELECT 1 FROM notification_permission np"
                        + " WHERE np.recipient_user_id = :recipient"
                        + " AND np.actor_user_id <=> :actorUserId"
                        + " AND np.actor_remote_id <=> :actorRemoteId),"
                        + " CASE WHEN :actorUserId IS NULL THEN EXISTS (SELECT 1"
                        + " FROM federation_following ff WHERE ff.user_id = :recipient"
                        + " AND ff.remote_actor_id = :actorRemoteId AND ff.accepted_at IS NOT NULL)"
                        + " ELSE EXISTS (SELECT 1 FROM user_follow uf WHERE uf.follower_id = :recipient"
                        + " AND uf.following_id = :actorUserId) END,"
                        + " CASE WHEN :actorUserId IS NULL THEN EXISTS (SELECT 1"
                        + " FROM federation_follower fr WHERE fr.user_id = :recipient"
                        + " AND fr.remote_actor_id = :actorRemoteId AND fr.accepted_at <= :settled)"
                        + " ELSE EXISTS (SELECT 1 FROM user_follow uf WHERE uf.follower_id = :actorUserId"
                        + " AND uf.following_id = :recipient AND uf.created_at <= :settled) END,"
                        + " CASE WHEN :actorUserId IS NULL THEN EXISTS (SELECT 1"
                        + " FROM federation_remote_actor ra WHERE ra.id = :actorRemoteId"
                        + " AND ra.created_at > :fresh)"
                        + " ELSE EXISTS (SELECT 1 FROM users u WHERE u.id = :actorUserId"
                        + " AND u.created_at > :fresh) END,"
                        + " EXISTS (SELECT 1 FROM note n WHERE n.id = :mentionNote"
                        + " AND n.visibility = 'DIRECT')"
                        + " FROM (SELECT 1) one"
                        + " LEFT JOIN notification_policy p ON p.user_id = :recipient")
                .setParameter("recipient", recipientUserId)
                .setParameter("type", type.name())
                .setParameter("actorUserId", actorUserId)
                .setParameter("actorRemoteId", actorRemoteId)
                .setParameter("settled", now.minus(SETTLED_FOLLOWER))
                .setParameter("fresh", now.minus(NEW_ACCOUNT))
                .setParameter("mentionNote", mentionNoteId)
                .getSingleResult();
    return new NotificationSender(
        truth(row[4]),
        row[0] == null ? null : policy(row, 0),
        truth(row[5]),
        truth(row[6]),
        truth(row[7]),
        truth(row[8]),
        truth(row[9]));
  }

  @Override
  public void permit(Long recipientUserId, Long actorUserId, Long actorRemoteId) {
    em.createNativeQuery(
            "INSERT INTO notification_permission"
                + " (recipient_user_id, actor_user_id, actor_remote_id, created_at)"
                + " SELECT :recipient, :actorUserId, :actorRemoteId, :now FROM (SELECT 1) one"
                + " WHERE NOT EXISTS (SELECT 1 FROM notification_permission np"
                + " WHERE np.recipient_user_id = :recipient AND np.actor_user_id <=> :actorUserId"
                + " AND np.actor_remote_id <=> :actorRemoteId)")
        .setParameter("recipient", recipientUserId)
        .setParameter("actorUserId", actorUserId)
        .setParameter("actorRemoteId", actorRemoteId)
        .setParameter("now", Instant.now())
        .executeUpdate();
  }

  private static NotificationPolicy policy(Object[] row, int from) {
    return new NotificationPolicy(
        NotificationPolicyLevel.valueOf(row[from].toString()),
        NotificationPolicyLevel.valueOf(row[from + 1].toString()),
        NotificationPolicyLevel.valueOf(row[from + 2].toString()),
        NotificationPolicyLevel.valueOf(row[from + 3].toString()));
  }

  private static boolean truth(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean b -> b;
      case Number n -> n.intValue() != 0;
      default -> Boolean.parseBoolean(value.toString());
    };
  }
}
