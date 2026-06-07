package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostRepository extends JpaRepository<PostEntity, Long> {

  List<PostEntity> findAllByIdIn(Collection<Long> ids);

  @Modifying
  @Query("update PostEntity p set p.likeCount = p.likeCount + 1 where p.id = :id")
  int incrementLikeCount(@Param("id") Long id);

  @Modifying
  @Query(
      "update PostEntity p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
  int decrementLikeCount(@Param("id") Long id);

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

  Optional<PostEntity> findByPreviewToken(String previewToken);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

  // Sort comes from the Pageable (per analytics sort dimension); the adapter appends an id-desc
  // tie-break so paging stays stable when many posts share a metric value (e.g. all 0).
  List<PostEntity> findByUserIdAndStatusIn(
      Long userId, Collection<PostStatus> statuses, Pageable pageable);

  long countByUserIdAndStatusIn(Long userId, Collection<PostStatus> statuses);

  // Single-status count (PUBLISHED) for the public profile's blog flag — resolved index-only by
  // idx_posts_user_status (user_id, status). Distinct from the Collection version, which spans
  // PUBLISHED+UNPUBLISHED for analytics and would over-count here.
  long countByUserIdAndStatus(Long userId, PostStatus status);

  List<PostEntity> findAllByStatusAndScheduledAtLessThanEqual(
      PostStatus status, Instant scheduledAt);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  // Trending = most views inside a recent window (`since`), newest as tiebreak. LEFT JOIN so every
  // published post still appears: posts with no recent views fall to a window count of 0 and sort
  // by
  // recency, keeping the feed full on a young platform while genuine traction floats to the top.
  // posts.view_count (lifetime) is deliberately unused here — ranking on that all-time counter is
  // exactly what made the old "trending" really just "most-viewed ever". Native because it crosses
  // to post_view_event; SELECT p.* + GROUP BY p.id is valid under MySQL's PK functional dependency.
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_view_event e ON e.post_id = p.id AND e.viewed_at >= :since "
              + "WHERE p.status = 'PUBLISHED' AND (:lang IS NULL OR p.language_tag = :lang) "
              + "GROUP BY p.id "
              + "ORDER BY COUNT(e.id) DESC, p.published_at DESC")
  List<PostEntity> findPublishedTrendingSince(
      @Param("since") Instant since, @Param("lang") String lang, Pageable pageable);

  // Global recent feed with an optional language filter (:lang null = all languages).
  @Query(
      "select p from PostEntity p where p.status = :status "
          + "and (:lang is null or p.languageTag = :lang) order by p.publishedAt desc")
  List<PostEntity> findPublishedRecent(
      @Param("status") PostStatus status, @Param("lang") String lang, Pageable pageable);

  @Query(
      "select count(p) from PostEntity p where p.status = :status "
          + "and (:lang is null or p.languageTag = :lang)")
  long countPublishedByLang(@Param("status") PostStatus status, @Param("lang") String lang);

  @Query(
      "select p from PostEntity p join p.tags t "
          + "where lower(t) = lower(:tag) and p.status = :status "
          + "order by p.publishedAt desc")
  List<PostEntity> findPublishedByTag(
      @Param("tag") String tag, @Param("status") PostStatus status, Pageable pageable);

  @Query(
      "select count(p) from PostEntity p join p.tags t "
          + "where lower(t) = lower(:tag) and p.status = :status")
  long countPublishedByTag(@Param("tag") String tag, @Param("status") PostStatus status);

  @Query(
      "select p from PostEntity p "
          + "where p.status = :status and (p.userId in :authorIds or p.seriesId in :seriesIds) "
          + "order by p.publishedAt desc")
  List<PostEntity> findPublishedByAuthorIdsOrSeriesIds(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("status") PostStatus status,
      Pageable pageable);

  @Query(
      "select count(p) from PostEntity p "
          + "where p.status = :status and (p.userId in :authorIds or p.seriesId in :seriesIds)")
  long countPublishedByAuthorIdsOrSeriesIds(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("status") PostStatus status);

  @Query(
      "select t, count(p) from PostEntity p join p.tags t "
          + "where p.status = :status group by t order by count(p) desc")
  List<Object[]> findPopularTags(@Param("status") PostStatus status, Pageable pageable);

  // Free-text feed search. Matches title / excerpt / any tag / author handle, newest first. `q` is
  // a
  // pre-lowercased, wildcard-escaped LIKE pattern (already wrapped in %…%) built by the adapter, so
  // the query only LIKEs it. The author match is a subquery against UserEntity because a post holds
  // a
  // raw userId, not an association — deleted authors are excluded there. `distinct` collapses the
  // row
  // fan-out from the tag join.
  @Query(
      "select distinct p from PostEntity p left join p.tags t "
          + "where p.status = :status and ("
          + "lower(p.title) like :q escape '!' "
          + "or lower(p.excerpt) like :q escape '!' "
          + "or lower(t) like :q escape '!' "
          + "or p.userId in (select u.id from UserEntity u "
          + "where lower(u.username) like :q escape '!' and u.deletedAt is null)) "
          + "and (:lang is null or p.languageTag = :lang) "
          + "order by p.publishedAt desc")
  List<PostEntity> searchPublished(
      @Param("q") String q,
      @Param("status") PostStatus status,
      @Param("lang") String lang,
      Pageable pageable);

  // Same match as searchPublished, but ranked by recent-window views (newest as tiebreak) so the
  // trending sort means the same thing inside search as it does on the main feed. Native to mirror
  // findPublishedTrendingSince: LEFT JOIN post_view_event for the windowed COUNT, LEFT JOIN
  // post_tag
  // for the tag match. COUNT(DISTINCT e.id) so the tag-join fan-out can't inflate the view count.
  // `q` is the pre-lowercased, wildcard-escaped %…% LIKE pattern built by the adapter.
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_view_event e ON e.post_id = p.id AND e.viewed_at >= :since "
              + "LEFT JOIN post_tag t ON t.post_id = p.id "
              + "WHERE p.status = 'PUBLISHED' AND ("
              + "LOWER(p.title) LIKE :q ESCAPE '!' "
              + "OR LOWER(p.excerpt) LIKE :q ESCAPE '!' "
              + "OR LOWER(t.tag) LIKE :q ESCAPE '!' "
              + "OR p.user_id IN (SELECT u.id FROM users u "
              + "WHERE LOWER(u.username) LIKE :q ESCAPE '!' AND u.deleted_at IS NULL)) "
              + "AND (:lang IS NULL OR p.language_tag = :lang) "
              + "GROUP BY p.id "
              + "ORDER BY COUNT(DISTINCT e.id) DESC, p.published_at DESC")
  List<PostEntity> searchPublishedTrendingSince(
      @Param("q") String q,
      @Param("since") Instant since,
      @Param("lang") String lang,
      Pageable pageable);

  @Query(
      "select count(distinct p) from PostEntity p left join p.tags t "
          + "where p.status = :status and ("
          + "lower(p.title) like :q escape '!' "
          + "or lower(p.excerpt) like :q escape '!' "
          + "or lower(t) like :q escape '!' "
          + "or p.userId in (select u.id from UserEntity u "
          + "where lower(u.username) like :q escape '!' and u.deletedAt is null)) "
          + "and (:lang is null or p.languageTag = :lang)")
  long countSearchPublished(
      @Param("q") String q, @Param("status") PostStatus status, @Param("lang") String lang);

  // Authors ranked for the discovery rail — most published posts first, total views as tiebreak.
  // Returns [userId, postCount, totalViews]; the service hydrates authors and drops deleted ones.
  @Query(
      "select p.userId, count(p), coalesce(sum(p.viewCount), 0) from PostEntity p "
          + "where p.status = :status group by p.userId "
          + "order by count(p) desc, coalesce(sum(p.viewCount), 0) desc")
  List<Object[]> findTopAuthorIds(@Param("status") PostStatus status, Pageable pageable);

  // Series ranked for cross-author discovery — most recently active first. Counts only PUBLISHED
  // member posts and drops thin series via HAVING. Returns [seriesId, postCount, lastPublishedAt];
  // the service hydrates the series + author and drops deleted ones.
  @Query(
      "select p.seriesId, count(p), max(p.publishedAt) from PostEntity p "
          + "where p.status = :status and p.seriesId is not null "
          + "group by p.seriesId "
          + "having count(p) >= :minPosts "
          + "order by max(p.publishedAt) desc")
  List<Object[]> findActiveSeries(
      @Param("status") PostStatus status, @Param("minPosts") long minPosts, Pageable pageable);
}
