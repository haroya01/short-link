package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Purges the notification slice's user-owned rows on account hard delete — none of these FKs carry
 * ON DELETE CASCADE. Covers the blog interaction inbox ({@code notification}, both as recipient and
 * as the actor referenced from other inboxes), the link-owner notification feed ({@code
 * link_notification}), and its per-type opt-outs ({@code notification_preference}).
 */
@Repository
class NotificationUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery(
            "DELETE FROM notification WHERE recipient_user_id = :userId"
                + " OR actor_user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM link_notification WHERE recipient_user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM notification_preference WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
