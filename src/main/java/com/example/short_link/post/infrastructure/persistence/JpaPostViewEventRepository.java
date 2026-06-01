package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostViewEventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostViewEventRepository extends JpaRepository<PostViewEventEntity, Long> {

  /** Projection for the GROUP BY DATE(viewed_at) aggregation — alias names map to the getters. */
  interface DailyViewRow {
    LocalDate getViewDate();

    long getViews();
  }

  // Native because it groups on a SQL date function over post_view_event, mirroring the trending
  // window query's native style. UTC dates (viewed_at is stored as an Instant) — good enough for v0
  // dashboards; per-author-timezone bucketing would be a later refinement.
  @Query(
      nativeQuery = true,
      value =
          "SELECT DATE(e.viewed_at) AS viewDate, COUNT(*) AS views "
              + "FROM post_view_event e "
              + "WHERE e.post_id = :postId AND e.viewed_at >= :since "
              + "GROUP BY DATE(e.viewed_at) "
              + "ORDER BY viewDate")
  List<DailyViewRow> countDailyByPostId(
      @Param("postId") Long postId, @Param("since") Instant since);

  @Query(
      nativeQuery = true,
      value =
          "SELECT DATE(e.viewed_at) AS viewDate, COUNT(*) AS views "
              + "FROM post_view_event e "
              + "JOIN posts p ON p.id = e.post_id "
              + "WHERE p.user_id = :userId AND e.viewed_at >= :since "
              + "GROUP BY DATE(e.viewed_at) "
              + "ORDER BY viewDate")
  List<DailyViewRow> countDailyByUserId(
      @Param("userId") Long userId, @Param("since") Instant since);
}
