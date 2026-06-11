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

  /** Projection for the GROUP BY DATE(viewed_at) aggregation — alias names map to the getters. */
  interface DailyViewRow {
    LocalDate getViewDate();

    long getViews();
  }

  /** One distinct (post, human-reader) pair — alias names map to the getters. */
  interface ReaderRow {
    Long getPostId();

    String getVisitorHash();
  }

  // Distinct human readers (visitor_hash) per post for a set of posts — powers the series
  // read-through funnel, where we intersect adjacent episodes' reader sets. Bots and hashless
  // (pre-V80) rows are dropped so the funnel reflects real continued reading.
  @Query(
      nativeQuery = true,
      value =
          "SELECT e.post_id AS postId, e.visitor_hash AS visitorHash "
              + "FROM post_view_event e "
              + "WHERE e.post_id IN (:postIds) AND e.visitor_hash IS NOT NULL AND e.is_bot = FALSE "
              + "GROUP BY e.post_id, e.visitor_hash")
  List<ReaderRow> findDistinctReaders(@Param("postIds") Collection<Long> postIds);

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

  /** Projection for the GROUP BY referrer_host aggregation — alias names map to the getters. */
  interface ReferrerRow {
    String getHost();

    long getViews();
  }

  // 작가 전체(post join)의 윈도우 유입 호스트 집계 — 개요 대시보드의 "유입 경로". 사람 조회만
  // (is_bot = FALSE), referrer 없는 direct 는 제외(글 단위 독자 분석의 topReferrerHosts 와 같은
  // 의미론). daily-by-user 와 같은 native 스타일이고, 동률은 host 로 고정해 페이지가 안 흔들린다.
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
