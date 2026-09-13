package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Delete explicitly because these user-owned rows have no ON DELETE CASCADE; include notifications
 * referencing the user as actor in other recipients' inboxes.
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
    // blog_notification_preference에는 users FK가 없어 명시적으로 삭제해야 한다.
    em.createNativeQuery("DELETE FROM blog_notification_preference WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
