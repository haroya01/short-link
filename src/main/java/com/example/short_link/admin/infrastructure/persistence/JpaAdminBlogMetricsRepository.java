package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminBlogMetricsRepository.TopPostRow;
import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Native cross-table aggregates for the blog admin metrics. Rooted at {@link PostEntity} only for
 * Spring Data plumbing — every query below is native SQL and joins the {@code posts} / {@code
 * users} / {@code post_view_event} / {@code abuse_report} tables directly, the same approach {@code
 * JpaAdminAnalyticsRepository} takes for the links metrics.
 */
public interface JpaAdminBlogMetricsRepository extends JpaRepository<PostEntity, Long> {

  @Query(value = "SELECT COUNT(*) FROM posts WHERE status = 'PUBLISHED'", nativeQuery = true)
  long totalPublishedPosts();

  @Query(
      value = "SELECT CAST(COALESCE(SUM(view_count), 0) AS SIGNED) FROM posts",
      nativeQuery = true)
  long totalReads();

  @Query(
      value =
          "SELECT COUNT(*) FROM ("
              + "SELECT p.user_id FROM posts p "
              + "WHERE p.published_at IS NOT NULL AND p.published_at >= :since "
              + "UNION "
              + "SELECT p.user_id FROM post_view_event e JOIN posts p ON p.id = e.post_id "
              + "WHERE e.is_bot = 0 AND e.viewed_at >= :since"
              + ") active_authors",
      nativeQuery = true)
  long activeAuthorsSince(@Param("since") Instant since);

  @Query(
      value = "SELECT COUNT(*) FROM abuse_report WHERE status IN ('OPEN', 'REVIEWING')",
      nativeQuery = true)
  long openReportCount();

  @Query(
      value =
          "SELECT p.id AS id, p.title AS title, p.slug AS slug, u.username AS authorHandle, "
              + "p.view_count AS `reads` "
              + "FROM posts p LEFT JOIN users u ON u.id = p.user_id "
              + "WHERE p.status = 'PUBLISHED' "
              + "ORDER BY p.view_count DESC, p.id DESC "
              + "LIMIT 5",
      nativeQuery = true)
  List<TopPostRow> topPostsByReads();
}
