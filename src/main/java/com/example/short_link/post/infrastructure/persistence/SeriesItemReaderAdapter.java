package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesFeedNote;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

// Notes live in the note module; a series reads only the columns it lists, straight from the table.
// Typed scalars let Hibernate read the times the way it writes an entity's Instant.
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
  @SuppressWarnings("unchecked")
  public Map<Long, SeriesFeedNote> feedNotes(Collection<Long> noteIds) {
    Map<Long, SeriesFeedNote> notes = new HashMap<>();
    if (noteIds.isEmpty()) {
      return notes;
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT n.id, n.body, n.content_warning, n.created_at, u.id AS author_id,"
                    + " u.username, u.bio, u.avatar_url, u.display_name, s.id AS series_id,"
                    + " s.slug, s.title"
                    + " FROM note n"
                    + " JOIN users u ON u.id = n.user_id AND u.deleted_at IS NULL"
                    + " AND u.username IS NOT NULL"
                    + " JOIN series_item i ON i.item_type = 'NOTE' AND i.ref_id = n.id"
                    + " JOIN series s ON s.id = i.series_id"
                    + " WHERE n.id IN (:ids) AND n.visibility IN ('PUBLIC', 'UNLISTED')")
            .setParameter("ids", noteIds)
            .unwrap(NativeQuery.class)
            .addScalar("id", Long.class)
            .addScalar("body", String.class)
            .addScalar("content_warning", String.class)
            .addScalar("created_at", Instant.class)
            .addScalar("author_id", Long.class)
            .addScalar("username", String.class)
            .addScalar("bio", String.class)
            .addScalar("avatar_url", String.class)
            .addScalar("display_name", String.class)
            .addScalar("series_id", Long.class)
            .addScalar("slug", String.class)
            .addScalar("title", String.class)
            .getResultList();
    for (Object[] row : rows) {
      Long id = (Long) row[0];
      notes.put(
          id,
          new SeriesFeedNote(
              id,
              (String) row[1],
              (String) row[2],
              (Instant) row[3],
              new SeriesFeedNote.Author(
                  (Long) row[4],
                  (String) row[5],
                  (String) row[6],
                  (String) row[7],
                  (String) row[8]),
              (Long) row[9],
              (String) row[10],
              (String) row[11]));
    }
    return notes;
  }

  @Override
  public List<SeriesEntry> readableEntries(Long seriesId) {
    return readableEntries(List.of(seriesId)).getOrDefault(seriesId, List.of());
  }

  @Override
  public Map<Long, List<SeriesEntry>> readableEntries(Collection<Long> seriesIds) {
    Map<Long, List<SeriesEntry>> bySeries = new LinkedHashMap<>();
    if (seriesIds.isEmpty()) {
      return bySeries;
    }
    for (Object[] row : entryRows(seriesIds)) {
      SeriesItemType type = SeriesItemType.valueOf((String) row[1]);
      Long refId = (Long) row[2];
      SeriesEntry entry =
          type == SeriesItemType.POST
              ? new SeriesEntry(
                  type, refId, (String) row[3], (String) row[4], (String) row[5], (Instant) row[8])
              : new SeriesEntry(
                  type,
                  refId,
                  null,
                  SeriesNote.excerptOf((String) row[6], (String) row[7]),
                  null,
                  (Instant) row[8]);
      bySeries.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add(entry);
    }
    return bySeries;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<SeriesActivity> activeSeries(int minItems, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT x.series_id, COUNT(*) AS item_count, MAX(x.item_at) AS last_at FROM ("
                    + " SELECT p.series_id, p.published_at AS item_at FROM posts p"
                    + " WHERE p.status = 'PUBLISHED' AND p.series_id IS NOT NULL"
                    + " AND p.body_text_length >= :minBody"
                    + " UNION ALL"
                    + " SELECT i.series_id, n.created_at FROM series_item i"
                    + " JOIN note n ON n.id = i.ref_id"
                    + " WHERE i.item_type = 'NOTE' AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                    + ") x GROUP BY x.series_id HAVING COUNT(*) >= :minItems"
                    + " ORDER BY last_at DESC, x.series_id DESC LIMIT :limit")
            .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
            .setParameter("minItems", minItems)
            .setParameter("limit", limit)
            .unwrap(NativeQuery.class)
            .addScalar("series_id", Long.class)
            .addScalar("item_count", Long.class)
            .addScalar("last_at", Instant.class)
            .getResultList();
    return rows.stream()
        .map(row -> new SeriesActivity((Long) row[0], (Long) row[1], (Instant) row[2]))
        .toList();
  }

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

  @SuppressWarnings("unchecked")
  private List<Object[]> entryRows(Collection<Long> seriesIds) {
    return em.createNativeQuery(
            "SELECT i.series_id, i.item_type, i.ref_id, p.slug, p.title, p.og_image_url, n.body,"
                + " n.content_warning, COALESCE(p.published_at, n.created_at) AS item_at"
                + " FROM series_item i"
                + " LEFT JOIN posts p ON i.item_type = 'POST' AND p.id = i.ref_id"
                + " AND p.status = 'PUBLISHED'"
                + " LEFT JOIN note n ON i.item_type = 'NOTE' AND n.id = i.ref_id"
                + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
                + " WHERE i.series_id IN (:ids) AND (p.id IS NOT NULL OR n.id IS NOT NULL)"
                + " ORDER BY i.series_id, i.position, i.id")
        .setParameter("ids", seriesIds)
        .unwrap(NativeQuery.class)
        .addScalar("series_id", Long.class)
        .addScalar("item_type", String.class)
        .addScalar("ref_id", Long.class)
        .addScalar("slug", String.class)
        .addScalar("title", String.class)
        .addScalar("og_image_url", String.class)
        .addScalar("body", String.class)
        .addScalar("content_warning", String.class)
        .addScalar("item_at", Instant.class)
        .getResultList();
  }

  private static boolean shared(String visibility) {
    return "PUBLIC".equals(visibility) || "UNLISTED".equals(visibility);
  }
}
