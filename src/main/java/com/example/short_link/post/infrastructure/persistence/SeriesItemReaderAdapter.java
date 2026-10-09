package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

// Notes live in the note module; a series reads only the columns it lists, straight from the table.
@Repository
class SeriesItemReaderAdapter implements SeriesItemReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, SeriesNote> notes(Collection<Long> noteIds) {
    Map<Long, SeriesNote> notes = new HashMap<>();
    if (noteIds.isEmpty()) {
      return notes;
    }
    for (Object[] row : noteRows(noteIds)) {
      Long id = (Long) row[0];
      notes.put(
          id,
          new SeriesNote(
              id,
              (Long) row[1],
              (String) row[2],
              (String) row[3],
              (Instant) row[4],
              shared((String) row[5])));
    }
    return notes;
  }

  @Override
  public List<SeriesEntry> readableEntries(Long seriesId) {
    List<SeriesEntry> entries = new ArrayList<>();
    for (Object raw :
        em.createNativeQuery(
                "SELECT i.item_type, i.ref_id, p.slug, p.title, n.body, n.content_warning"
                    + " FROM series_item i"
                    + " LEFT JOIN posts p ON i.item_type = 'POST' AND p.id = i.ref_id"
                    + " AND p.status = 'PUBLISHED'"
                    + " LEFT JOIN note n ON i.item_type = 'NOTE' AND n.id = i.ref_id"
                    + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                    + " WHERE i.series_id = :seriesId AND (p.id IS NOT NULL OR n.id IS NOT NULL)"
                    + " ORDER BY i.position, i.id")
            .setParameter("seriesId", seriesId)
            .getResultList()) {
      Object[] row = (Object[]) raw;
      SeriesItemType type = SeriesItemType.valueOf((String) row[0]);
      Long refId = ((Number) row[1]).longValue();
      entries.add(
          type == SeriesItemType.POST
              ? new SeriesEntry(type, refId, (String) row[2], (String) row[3])
              : new SeriesEntry(
                  type, refId, null, SeriesNote.excerptOf((String) row[4], (String) row[5])));
    }
    return entries;
  }

  // Typed scalars let Hibernate read created_at the way it writes an entity's Instant.
  @SuppressWarnings("unchecked")
  private List<Object[]> noteRows(Collection<Long> noteIds) {
    return em.createNativeQuery(
            "SELECT n.id, n.user_id, n.body, n.content_warning, n.created_at, n.visibility"
                + " FROM note n WHERE n.id IN (:ids) AND n.user_id IS NOT NULL")
        .setParameter("ids", noteIds)
        .unwrap(NativeQuery.class)
        .addScalar("id", Long.class)
        .addScalar("user_id", Long.class)
        .addScalar("body", String.class)
        .addScalar("content_warning", String.class)
        .addScalar("created_at", Instant.class)
        .addScalar("visibility", String.class)
        .getResultList();
  }

  private static boolean shared(String visibility) {
    return "PUBLIC".equals(visibility) || "UNLISTED".equals(visibility);
  }
}
