package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.note.Excerpt;
import com.example.short_link.common.note.NoteAccessSql;
import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.note.domain.NoteSeries;
import com.example.short_link.note.domain.repository.NoteSeriesReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Series live in the post module; a note reads its series' order straight from the tables.
@Repository
class NoteSeriesReaderAdapter implements NoteSeriesReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<NoteSeries> containing(Long noteId) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT s.id, s.slug, s.title, i.item_type, i.ref_id, p.slug, p.title, n.body,"
                    + " n.content_warning"
                    + " FROM series_item mine"
                    + " JOIN series s ON s.id = mine.series_id"
                    + " JOIN series_item i ON i.series_id = s.id"
                    + " LEFT JOIN posts p ON i.item_type = 'POST' AND p.id = i.ref_id AND "
                    + PostAccessSql.OPEN
                    + " LEFT JOIN note n ON i.item_type = 'NOTE' AND n.id = i.ref_id AND "
                    + NoteAccessSql.shareable("n")
                    + " WHERE mine.item_type = 'NOTE' AND mine.ref_id = :noteId"
                    + " AND (p.id IS NOT NULL OR n.id IS NOT NULL)"
                    + " ORDER BY i.position, i.id")
            .setParameter("noteId", noteId)
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    List<NoteSeries.Entry> entries = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] row = (Object[]) raw;
      String type = (String) row[3];
      Long refId = ((Number) row[4]).longValue();
      entries.add(
          "POST".equals(type)
              ? new NoteSeries.Entry(type, refId, (String) row[5], (String) row[6])
              : new NoteSeries.Entry(type, refId, null, excerpt((String) row[7], (String) row[8])));
    }
    Object[] first = (Object[]) rows.get(0);
    return Optional.of(
        new NoteSeries(
            ((Number) first[0]).longValue(), (String) first[1], (String) first[2], entries));
  }

  private static String excerpt(String body, String contentWarning) {
    return Excerpt.of(body, contentWarning);
  }
}
