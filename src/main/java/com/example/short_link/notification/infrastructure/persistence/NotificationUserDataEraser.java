package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Purges notifications on account hard delete: the recipient FK lacks ON DELETE CASCADE, and rows
 * the user actored in other inboxes point at interactions that are being erased with the account.
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
  }
}
