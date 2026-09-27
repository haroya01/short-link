package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostViewEventEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostViewEventRepository extends JpaRepository<PostViewEventEntity, Long> {

  interface DailyViewRow {
    LocalDate getViewDate();

    long getViews();
  }

  interface ReaderRow {
    Long getPostId();

    String getVisitorHash();
  }

  // Bots and hashless rows cannot contribute to the series reader-overlap funnel.
  @Query(
      nativeQuery = true,
      value =
          "SELECT e.post_id AS postId, e.visitor_hash AS visitorHash "
              + "FROM post_view_event e "
              + "WHERE e.post_id IN (:postIds) AND e.visitor_hash IS NOT NULL AND e.is_bot = FALSE "
              + "GROUP BY e.post_id, e.visitor_hash")
  List<ReaderRow> findDistinctReaders(@Param("postIds") Collection<Long> postIds);

  // Group by UTC date; viewed_at is stored as an Instant.
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

  interface ReferrerRow {
    String getHost();

    long getViews();
  }

  // 사람 조회만 집계하고 direct 유입은 제외한다. 동률은 host로 정렬해 순서를 고정한다.
  @Query(
      nativeQuery = true,
      value =
          "SELECT e.referrer_host AS host, COUNT(*) AS views "
              + "FROM post_view_event e "
              + "JOIN posts p ON p.id = e.post_id "
              + "WHERE p.user_id = :userId AND e.viewed_at >= :since "
              + "AND e.is_bot = FALSE AND e.referrer_host IS NOT NULL "
              + "GROUP BY e.referrer_host "
              + "ORDER BY views DESC, host "
              + "LIMIT :limit")
  List<ReferrerRow> topReferrerHostsByUser(
      @Param("userId") Long userId, @Param("since") Instant since, @Param("limit") int limit);
}
