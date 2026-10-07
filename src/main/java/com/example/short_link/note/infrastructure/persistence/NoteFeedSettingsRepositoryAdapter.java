package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.repository.NoteFeedSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class NoteFeedSettingsRepositoryAdapter implements NoteFeedSettingsRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public Preferences read(Long userId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT show_reposts, languages FROM note_feed_preference WHERE user_id = :user")
            .setParameter("user", userId)
            .getResultList();
    if (rows.isEmpty()) {
      return new Preferences(true, List.of());
    }
    Object[] row = (Object[]) rows.getFirst();
    String languages = (String) row[1];
    return new Preferences(
        Boolean.TRUE.equals(asBoolean(row[0])),
        languages == null || languages.isEmpty() ? List.of() : List.of(languages.split(",")));
  }

  @Override
  public void setLanguages(Long userId, List<String> languages) {
    em.createNativeQuery(
            "INSERT INTO note_feed_preference (user_id, show_reposts, languages, updated_at)"
                + " VALUES (:user, TRUE, :languages, NOW(6)) AS fresh"
                + " ON DUPLICATE KEY UPDATE languages = fresh.languages,"
                + " updated_at = fresh.updated_at")
        .setParameter("user", userId)
        .setParameter("languages", languages.isEmpty() ? null : String.join(",", languages))
        .executeUpdate();
  }

  @Override
  public void setShowsReposts(Long userId, boolean show) {
    em.createNativeQuery(
            "INSERT INTO note_feed_preference (user_id, show_reposts, updated_at)"
                + " VALUES (:user, :show, NOW(6)) AS fresh"
                + " ON DUPLICATE KEY UPDATE show_reposts = fresh.show_reposts,"
                + " updated_at = fresh.updated_at")
        .setParameter("user", userId)
        .setParameter("show", show)
        .executeUpdate();
  }

  @Override
  public boolean hidesRepostsOf(Long userId, Long otherUserId) {
    return !em.createNativeQuery(
            "SELECT 1 FROM note_repost_mute WHERE user_id = :user AND muted_user_id = :other")
        .setParameter("user", userId)
        .setParameter("other", otherUserId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void hideRepostsOf(Long userId, Long otherUserId) {
    em.createNativeQuery(
            "INSERT IGNORE INTO note_repost_mute (user_id, muted_user_id, created_at)"
                + " VALUES (:user, :other, NOW(6))")
        .setParameter("user", userId)
        .setParameter("other", otherUserId)
        .executeUpdate();
  }

  @Override
  public void showRepostsOf(Long userId, Long otherUserId) {
    em.createNativeQuery(
            "DELETE FROM note_repost_mute WHERE user_id = :user AND muted_user_id = :other")
        .setParameter("user", userId)
        .setParameter("other", otherUserId)
        .executeUpdate();
  }

  private static Boolean asBoolean(Object value) {
    return value instanceof Number number ? number.intValue() != 0 : (Boolean) value;
  }
}
