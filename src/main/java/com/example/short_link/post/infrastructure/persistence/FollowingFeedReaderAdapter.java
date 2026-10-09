package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.FollowingFeedRef;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.repository.FollowingFeedReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

// The post half matches the posts-only subscription feed: tags compare lowercased, and DISTINCT
// folds a post that several followed tags reach. Empty IN lists become values that match nothing.
@Repository
class FollowingFeedReaderAdapter implements FollowingFeedReader {

  private static final String POSTS =
      "SELECT DISTINCT 'POST' AS item_type, p.id AS id, NULL AS series_id, p.published_at AS item_at"
          + " FROM posts p LEFT JOIN post_tag t ON t.post_id = p.id"
          + " WHERE p.status = 'PUBLISHED'"
          + " AND (p.user_id IN (:authors) OR p.series_id IN (:series) OR LOWER(t.tag) IN (:tags))"
          + HeardSql.POST_AUTHOR;

  private static final String NOTES =
      "SELECT 'NOTE', n.id, i.series_id, n.created_at FROM series_item i"
          + " JOIN note n ON n.id = i.ref_id"
          + " WHERE i.item_type = 'NOTE' AND i.series_id IN (:series)"
          + " AND n.visibility IN ('PUBLIC', 'UNLISTED')"
          + HeardSql.authoredBy("n");

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public List<FollowingFeedRef> page(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int offset,
      int limit) {
    Query query =
        bind(
                em.createNativeQuery(
                    "SELECT u.item_type, u.id, u.series_id FROM ("
                        + POSTS
                        + " UNION ALL "
                        + NOTES
                        + ") u ORDER BY u.item_at DESC, u.id DESC LIMIT :limit OFFSET :offset"),
                viewerId,
                authorIds,
                seriesIds,
                tags)
            .setParameter("limit", limit)
            .setParameter("offset", offset);
    List<Object[]> rows =
        query
            .unwrap(NativeQuery.class)
            .addScalar("item_type", String.class)
            .addScalar("id", Long.class)
            .addScalar("series_id", Long.class)
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new FollowingFeedRef(
                    SeriesItemType.valueOf((String) row[0]), (Long) row[1], (Long) row[2]))
        .toList();
  }

  @Override
  public long count(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags) {
    Object total =
        bind(
                em.createNativeQuery(
                    "SELECT COUNT(*) FROM (" + POSTS + " UNION ALL " + NOTES + ") u"),
                viewerId,
                authorIds,
                seriesIds,
                tags)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  private static Query bind(
      Query query,
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags) {
    return query
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setParameter("authors", authorIds.isEmpty() ? List.of(-1L) : authorIds)
        .setParameter("series", seriesIds.isEmpty() ? List.of(-1L) : seriesIds)
        .setParameter("tags", tags.isEmpty() ? List.of("\u0000") : tags);
  }
}
