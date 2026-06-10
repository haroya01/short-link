package com.example.short_link.cta.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/** Purges CTAs on account hard delete — fk_cta_user lacks ON DELETE CASCADE. */
@Repository
class CtaUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery("DELETE FROM cta WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
