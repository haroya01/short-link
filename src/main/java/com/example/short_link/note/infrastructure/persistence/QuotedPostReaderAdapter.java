package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class QuotedPostReaderAdapter implements QuotedPostReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, QuotedPost> publishedByIds(Collection<Long> postIds) {
    Map<Long, QuotedPost> posts = new HashMap<>();
    if (postIds.isEmpty()) {
      return posts;
    }
    for (Object raw :
        em.createNativeQuery(
                "SELECT p.id, p.title, p.slug, u.username FROM posts p JOIN users u"
                    + " ON u.id = p.user_id WHERE p.id IN (:ids) AND p.status = 'PUBLISHED'"
                    + " AND u.deleted_at IS NULL AND u.username IS NOT NULL")
            .setParameter("ids", postIds)
            .getResultList()) {
      Object[] row = (Object[]) raw;
      Long id = ((Number) row[0]).longValue();
      posts.put(id, new QuotedPost(id, (String) row[1], (String) row[2], (String) row[3]));
    }
    return posts;
  }
}
