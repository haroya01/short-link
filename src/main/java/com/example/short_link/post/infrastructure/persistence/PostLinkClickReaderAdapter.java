package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickRollup;
import com.example.short_link.post.domain.PostLinkClick;
import com.example.short_link.post.domain.repository.PostLinkClickReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class PostLinkClickReaderAdapter implements PostLinkClickReader {

  @PersistenceContext private EntityManager em;

  @Override
  public long countByPostId(Long postId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT (SELECT COUNT(*) FROM click_event WHERE post_id = :postId "
                        + "AND is_bot = FALSE AND clicked_at >= "
                        + ClickRollup.ROLLED_UNTIL
                        + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
                        + "WHERE dim = "
                        + ClickRollup.POST
                        + " AND num = :postId)")
                .setParameter("postId", postId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByPostIdSince(Long postId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT (SELECT COUNT(*) FROM click_event WHERE post_id = :postId "
                        + "AND is_bot = FALSE AND clicked_at >= FROM_UNIXTIME(:since) AND clicked_at >= "
                        + ClickRollup.ROLLED_UNTIL
                        + ") + (SELECT COALESCE(SUM(clicks), 0) FROM click_daily_dim "
                        + "WHERE dim = "
                        + ClickRollup.POST
                        + " AND num = :postId AND day_start >= FROM_UNIXTIME(:since))")
                .setParameter("postId", postId)
                .setParameter("since", since.getEpochSecond())
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserId(Long userId) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT (SELECT COUNT(*) FROM click_event c JOIN posts p ON p.id = c.post_id "
                        + "WHERE p.user_id = :userId AND c.is_bot = FALSE AND c.clicked_at >= "
                        + ClickRollup.ROLLED_UNTIL
                        + ") + (SELECT COALESCE(SUM(d.clicks), 0) FROM click_daily_dim d "
                        + "JOIN posts p ON p.id = d.num WHERE d.dim = "
                        + ClickRollup.POST
                        + " AND p.user_id = :userId)")
                .setParameter("userId", userId)
                .getSingleResult())
        .longValue();
  }

  @Override
  public long countByUserIdSince(Long userId, Instant since) {
    return ((Number)
            em.createNativeQuery(
                    "SELECT (SELECT COUNT(*) FROM click_event c JOIN posts p ON p.id = c.post_id "
                        + "WHERE p.user_id = :userId AND c.is_bot = FALSE AND c.clicked_at >= FROM_UNIXTIME(:since) "
                        + "AND c.clicked_at >= "
                        + ClickRollup.ROLLED_UNTIL
                        + ") + (SELECT COALESCE(SUM(d.clicks), 0) FROM click_daily_dim d "
                        + "JOIN posts p ON p.id = d.num WHERE d.dim = "
                        + ClickRollup.POST
                        + " AND p.user_id = :userId AND d.day_start >= FROM_UNIXTIME(:since))")
                .setParameter("userId", userId)
                .setParameter("since", since.getEpochSecond())
                .getSingleResult())
        .longValue();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostLinkClick> breakdownByPostId(Long postId, int limit) {
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT l.short_code, l.original_url, CAST(SUM(u.n) AS SIGNED) AS clicks "
                    + "FROM (SELECT link_id, COUNT(*) AS n FROM click_event "
                    + "WHERE post_id = :postId AND is_bot = FALSE AND clicked_at >= "
                    + ClickRollup.ROLLED_UNTIL
                    + " GROUP BY link_id UNION ALL SELECT link_id, SUM(clicks) FROM click_daily_dim "
                    + "WHERE dim = "
                    + ClickRollup.POST
                    + " AND num = :postId GROUP BY link_id) u JOIN link l ON l.id = u.link_id "
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
