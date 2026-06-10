package com.example.short_link.campaign.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Purges campaigns on account hard delete. campaign.owner_id has no FK so orphans would survive
 * silently; campaign_batch cascades from campaign.
 */
@Repository
class CampaignUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery("DELETE FROM campaign WHERE owner_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
