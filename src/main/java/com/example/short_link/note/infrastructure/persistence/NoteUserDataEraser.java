package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

// Notes and their image rows go with the user by ON DELETE CASCADE, but this user's likes and
// reposts of other people's notes (no cascading FK on the user) do not.
@Repository
class NoteUserDataEraser implements UserDataEraser {

  @PersistenceContext private EntityManager em;

  @Override
  public void eraseFor(long userId) {
    em.createNativeQuery("DELETE FROM note_like WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_repost WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_bookmark WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery(
            "DELETE FROM note_repost_mute WHERE user_id = :userId OR muted_user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_feed_preference WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_recipient WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_list_member WHERE member_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_list WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_poll_vote WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
    em.createNativeQuery("DELETE FROM note_filter WHERE user_id = :userId")
        .setParameter("userId", userId)
        .executeUpdate();
  }
}
