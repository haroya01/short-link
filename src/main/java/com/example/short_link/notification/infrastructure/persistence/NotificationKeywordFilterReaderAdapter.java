package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.policy.KeywordFilter;
import com.example.short_link.notification.domain.repository.NotificationKeywordFilterReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

// Uses a native query to avoid an entity dependency on the note module.
@Repository
class NotificationKeywordFilterReaderAdapter implements NotificationKeywordFilterReader {

  // The note module stores a filter's contexts as bits by declaration order; notifications is the
  // fifth (NoteFilterEntity.Context.NOTIFICATIONS).
  private static final int NOTIFICATIONS_CONTEXT = 1 << 4;

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, List<KeywordFilter>> activeFor(Collection<Long> userIds, Instant now) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    List<?> rows =
        em.createNativeQuery(
                "SELECT user_id, phrase, whole_word, filter_action FROM note_filter"
                    + " WHERE user_id IN (:ids) AND contexts & :context <> 0"
                    + " AND (expires_at IS NULL OR expires_at > :now)")
            .setParameter("ids", userIds)
            .setParameter("context", NOTIFICATIONS_CONTEXT)
            .setParameter("now", now)
            .getResultList();
    Map<Long, List<KeywordFilter>> byUser = new HashMap<>();
    for (Object raw : rows) {
      Object[] row = (Object[]) raw;
      byUser
          .computeIfAbsent(((Number) row[0]).longValue(), id -> new ArrayList<>())
          .add(new KeywordFilter((String) row[1], truth(row[2]), "HIDE".equals(row[3])));
    }
    return byUser;
  }

  private static boolean truth(Object value) {
    return value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
  }
}
