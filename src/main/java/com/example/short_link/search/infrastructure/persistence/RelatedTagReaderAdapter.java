package com.example.short_link.search.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.search.domain.RelatedTag;
import com.example.short_link.search.domain.repository.RelatedTagReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class RelatedTagReaderAdapter implements RelatedTagReader {

  @PersistenceContext private EntityManager em;

  @Override
  public List<RelatedTag> cooccurring(String match, Collection<String> words, int scan, int limit) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT r.tag, SUM(r.kind = 1), SUM(r.kind = 2) FROM ("
                    + "SELECT t.tag, 1 AS kind, m.author FROM ("
                    + "SELECT p.id, p.user_id AS author FROM posts p"
                    + " JOIN post_search_text s ON s.post_id = p.id"
                    + " WHERE MATCH(s.search_text) AGAINST(:match IN BOOLEAN MODE) AND "
                    + PostAccessSql.LISTED
                    + " ORDER BY p.published_at DESC LIMIT :scan) m"
                    + " JOIN post_tag t ON t.post_id = m.id"
                    + " UNION ALL "
                    + "SELECT g.tag, 2, m.author FROM ("
                    + "SELECT n.id, COALESCE(n.user_id, -n.remote_actor_id) AS author FROM note n"
                    + " WHERE n.visibility = 'PUBLIC'"
                    + " AND MATCH(n.body) AGAINST(:match IN BOOLEAN MODE)"
                    + " AND (n.remote_actor_id IS NULL OR EXISTS ("
                    + "SELECT 1 FROM federation_remote_actor ia WHERE ia.id = n.remote_actor_id"
                    + " AND ia.indexable AND NOT "
                    + ServerBlockSql.blocks("ia.domain")
                    + "))"
                    + " ORDER BY n.id DESC LIMIT :scan) m"
                    + " JOIN note_tag g ON g.note_id = m.id"
                    + ") r WHERE r.tag NOT IN (:words)"
                    + " GROUP BY r.tag"
                    + " ORDER BY COUNT(DISTINCT r.author) DESC, COUNT(*) DESC, r.tag"
                    + " LIMIT :limit")
            .setParameter("match", match)
            .setParameter("scan", scan)
            .setParameter("words", words)
            .setParameter("limit", limit)
            .getResultList();
    List<RelatedTag> tags = new ArrayList<>(rows.size());
    for (Object raw : rows) {
      Object[] row = (Object[]) raw;
      tags.add(
          new RelatedTag(
              (String) row[0], ((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
    }
    return tags;
  }
}
