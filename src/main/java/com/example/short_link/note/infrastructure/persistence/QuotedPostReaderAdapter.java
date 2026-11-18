package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class QuotedPostReaderAdapter implements QuotedPostReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Map<Long, QuotedPost> quotedWhereTheyStand(Collection<Long> postIds) {
    Map<Long, QuotedPost> posts = new HashMap<>();
    if (postIds.isEmpty()) {
      return posts;
    }
    for (Object raw :
        em.createNativeQuery(
                "SELECT p.id, p.title, p.slug, u.username, p.user_id, p.audience,"
                    + " p.blocked_countries, p.room_id FROM posts p"
                    + " JOIN users u ON u.id = p.user_id WHERE p.id IN (:ids) AND "
                    + PostAccessSql.quotedWhereItStands("p")
                    + " AND u.deleted_at IS NULL AND u.username IS NOT NULL")
            .setParameter("ids", postIds)
            .getResultList()) {
      Object[] row = (Object[]) raw;
      Long id = ((Number) row[0]).longValue();
      posts.put(
          id,
          new QuotedPost(
              id,
              (String) row[1],
              (String) row[2],
              (String) row[3],
              ((Number) row[4]).longValue(),
              PostAudience.valueOf((String) row[5]),
              row[6] != null,
              row[7] == null ? null : ((Number) row[7]).longValue()));
    }
    return posts;
  }

  @Override
  public Optional<Long> roomOf(Long postId) {
    List<?> rooms =
        em.createNativeQuery(
                "SELECT p.room_id FROM posts p WHERE p.id = :id AND p.room_id IS NOT NULL")
            .setParameter("id", postId)
            .getResultList();
    return rooms.isEmpty()
        ? Optional.empty()
        : Optional.of(((Number) rooms.getFirst()).longValue());
  }
}
