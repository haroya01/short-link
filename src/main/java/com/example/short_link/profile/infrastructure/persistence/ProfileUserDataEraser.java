package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * These user-owned tables lack users foreign keys and must be cleared explicitly on account
 * deletion.
 */
@Repository
class ProfileUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    execute("DELETE FROM email_lead WHERE user_id = :userId", userId);
    execute("DELETE FROM profile_block WHERE user_id = :userId", userId);
    execute("DELETE FROM profile_visit_event WHERE profile_user_id = :userId", userId);
    execute("DELETE FROM username_history WHERE user_id = :userId", userId);
  }

  private void execute(String sql, long userId) {
    em.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
  }
}
