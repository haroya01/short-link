package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.user.UserDataEraser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

// Notes and their image rows go with the user by ON DELETE CASCADE, but the stored image files and
// this user's likes and reposts of other people's notes (no cascading FK on the user) do not.
@Slf4j
@Repository
@RequiredArgsConstructor
class NoteUserDataEraser implements UserDataEraser {

  private final ObjectStorage storage;

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
    if (!storage.isConfigured()) {
      return;
    }
    List<?> keys =
        em.createNativeQuery(
                "SELECT m.storage_key FROM note_media m JOIN note n ON n.id = m.note_id"
                    + " WHERE n.user_id = :userId")
            .setParameter("userId", userId)
            .getResultList();
    for (Object key : keys) {
      try {
        storage.delete((String) key);
      } catch (ObjectStorageException e) {
        log.warn("failed to delete note image key={}", key, e);
      }
    }
  }
}
