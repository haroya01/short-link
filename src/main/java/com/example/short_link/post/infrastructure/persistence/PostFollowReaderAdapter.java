package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostFollowReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * Counts {@code user_follow} rows attributed to a post (source_post_id) or to all of an author's
 * posts. Uses native queries against user_follow so the post module needn't depend on the user
 * module's entity — the cross-product seam is a single, read-only SQL lookup on source_post_id.
 */
@Repository
class PostFollowReaderAdapter implements PostFollowReader {

  @PersistenceContext private EntityManager em;

  @Override
  public long countBySourcePostId(Long postId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM user_follow WHERE source_post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countBySourcePostIdSince(Long postId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM user_follow "
                        + "WHERE source_post_id = :postId AND created_at >= :since")
                .setParameter("postId", postId)
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserId(Long userId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM user_follow f JOIN posts p ON p.id = f.source_post_id "
                        + "WHERE p.user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserIdSince(Long userId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM user_follow f JOIN posts p ON p.id = f.source_post_id "
                        + "WHERE p.user_id = :userId AND f.created_at >= :since")
                .setParameter("userId", userId)
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Long, Long> countBySourcePostIdIn(Collection<Long> postIds) {
    Map<Long, Long> out = new HashMap<>();
    if (postIds.isEmpty()) {
      return out;
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT source_post_id, COUNT(*) FROM user_follow "
                    + "WHERE source_post_id IN (:ids) GROUP BY source_post_id")
            .setParameter("ids", postIds)
            .getResultList();
    for (Object[] row : rows) {
      out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }
    return out;
  }
}
