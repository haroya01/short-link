package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostLinkClick;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Counts kurl click_event rows attributed to a post (post_id) or to all of an author's posts. Uses
 * a native query against click_event so the post module needn't depend on the link module's entity
 * — the cross-product seam is a single, read-only SQL join on post_id.
 */
@Repository
class PostLinkClickReaderAdapter implements PostLinkClickReader {

  @PersistenceContext private EntityManager em;

  @Override
  public long countByPostId(Long postId) {
    return ((Number)
            em.createNativeQuery("SELECT COUNT(*) FROM click_event WHERE post_id = :postId")
                .setParameter("postId", postId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByPostIdSince(Long postId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM click_event WHERE post_id = :postId AND clicked_at >= :since")
                .setParameter("postId", postId)
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserId(Long userId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM click_event c JOIN posts p ON p.id = c.post_id "
                        + "WHERE p.user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserIdSince(Long userId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT COUNT(*) FROM click_event c JOIN posts p ON p.id = c.post_id "
                        + "WHERE p.user_id = :userId AND c.clicked_at >= :since")
                .setParameter("userId", userId)
                .setParameter("since", since)
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostLinkClick> breakdownByPostId(Long postId, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT l.short_code, l.original_url, COUNT(*) AS clicks "
                    + "FROM click_event c JOIN link l ON l.id = c.link_id "
                    + "WHERE c.post_id = :postId "
                    + "GROUP BY l.id, l.short_code, l.original_url "
                    + "ORDER BY clicks DESC, l.id DESC")
            .setParameter("postId", postId)
            .setMaxResults(Math.max(1, limit))
            .getResultList();
    return rows.stream()
        .map(
            row ->
                new PostLinkClick((String) row[0], (String) row[1], ((Number) row[2]).longValue()))
        .toList();
  }
}
