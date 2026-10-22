package com.example.short_link.room.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

// Membership, requests and removals go with the account by cascade. A room it owned passes to
// whoever
// has been in it longest, and its invites stop letting anyone in.
@Repository
class RoomUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery(
            "UPDATE room r SET r.owner_id = (SELECT m.user_id FROM room_member m"
                + " WHERE m.room_id = r.id AND m.user_id <> :user"
                + " ORDER BY m.joined_at, m.user_id LIMIT 1)"
                + " WHERE r.owner_id = :user AND EXISTS (SELECT 1 FROM room_member o"
                + " WHERE o.room_id = r.id AND o.user_id <> :user)")
        .setParameter("user", userId)
        .executeUpdate();
    em.createNativeQuery(
            "UPDATE room_invite SET disabled_at = UTC_TIMESTAMP(6)"
                + " WHERE creator_id = :user AND disabled_at IS NULL")
        .setParameter("user", userId)
        .executeUpdate();
  }
}
