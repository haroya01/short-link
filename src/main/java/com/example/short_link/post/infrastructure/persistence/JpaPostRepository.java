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

  // The "following" feed union: posts whose author I follow OR whose series I subscribe to OR which
  // carry a tag I follow. LEFT JOIN p.tags so the `lower(t) in :tags` (case-insensitive) match
  // works;
  // `distinct` collapses the row fan-out that join produces for multi-tag posts. Callers pass a
  // no-match sentinel for any empty side (JPQL `in ()` is invalid).
  @Query(
      "select distinct p from PostEntity p left join p.tags t "
          + "where p.status = :status "
          + "and (p.userId in :authorIds or p.seriesId in :seriesIds or lower(t) in :tags) "
          + "order by p.publishedAt desc")
  List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("tags") Collection<String> tags,
      @Param("status") PostStatus status,
      Pageable pageable);

  @Query(
      "select count(distinct p) from PostEntity p left join p.tags t "
          + "where p.status = :status "
          + "and (p.userId in :authorIds or p.seriesId in :seriesIds or lower(t) in :tags)")
  long countPublishedByAuthorsSeriesOrTags(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("tags") Collection<String> tags,
      @Param("status") PostStatus status);

  // "For You" — posts carrying any interest tag (inner join: a match is required), not the reader's
  // own and not already read. `distinct` collapses the multi-tag join fan-out. excludeIds is never
  // empty (caller passes a no-match sentinel), so `not in` stays valid.
  @Query(
      "select distinct p from PostEntity p join p.tags t "
          + "where p.status = :status and p.userId <> :userId "
          + "and lower(t) in :tags and p.id not in :excludeIds "
          + "order by p.publishedAt desc")
  List<PostEntity> findForYouCandidates(
      @Param("userId") Long userId,
      @Param("tags") Collection<String> tags,
      @Param("excludeIds") Collection<Long> excludeIds,
      @Param("status") PostStatus status,
      Pageable pageable);

  @Query(
      "select count(distinct p) from PostEntity p join p.tags t "
          + "where p.status = :status and p.userId <> :userId "
          + "and lower(t) in :tags and p.id not in :excludeIds")
  long countForYouCandidates(
      @Param("userId") Long userId,
      @Param("tags") Collection<String> tags,
      @Param("excludeIds") Collection<Long> excludeIds,
      @Param("status") PostStatus status);

  @Query(
      "select t, count(p) from PostEntity p join p.tags t "
          + "where p.status = :status group by t order by count(p) desc")
  List<Object[]> findPopularTags(@Param("status") PostStatus status, Pageable pageable);

  // Free-text feed search — now FULLTEXT(ngram) over the derived `search_text` column (title +
  // excerpt + tags + flattened body blocks), so the query reaches the body, not just the
  // title/tags.
  // Two matchers OR'd: (1) MATCH(search_text) AGAINST(:match IN BOOLEAN MODE) — the ngram
  // body/title match, and (2) the author-handle LIKE subquery kept verbatim (username lives on
  // `users`, not on a posts column, so it can't ride the FULLTEXT index). `:match` is the operator-
  // scrubbed query the adapter builds from raw input; `:like` is the pre-lowercased, wildcard-
  // escaped %…% pattern for the handle subquery. Native because MATCH() is not JPQL.
  //
  // ★ BOOLEAN MODE + 평문 토큰(no +/*/""): ngram+BOOLEAN의 프리픽스 연산자(term*)나
  //   구문("term")은 여러 바이그램으로 쪼개지는 한글 다바이그램 토큰을 깨뜨린다(실측: +"리다이렉트"=0건).
  //   반대로 NATURAL 모드는 한글은 잘 잡지만 영어는 흔한 바이그램(en,nt,on…) 하나만 겹쳐도 오매칭(실측:
  //   "frontend"가 세 글 모두 매칭). 연산자 없는 평문 BOOLEAN 항(implicit 바이그램 그룹)만이 한/영
  //   단어 둘 다 정확히 부분일치하고 관련성 점수도 준다(다단어는 "많이 겹칠수록 상위" OR 랭킹).
  //
  // recent 정렬: 관련성 무시, 최신순 — 예전 recent 검색과 정렬 계약 동일(본문까지 잡히는 것만 넓어졌다).
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "WHERE p.status = 'PUBLISHED' AND ("
              + "MATCH(p.search_text) AGAINST(:match IN BOOLEAN MODE) "
              + "OR p.user_id IN (SELECT u.id FROM users u "
              + "WHERE LOWER(u.username) LIKE :like ESCAPE '!' AND u.deleted_at IS NULL)) "
              + "AND (:lang IS NULL OR p.language_tag = :lang) "
              + "ORDER BY p.published_at DESC")
  List<PostEntity> searchPublishedRecent(
      @Param("match") String match,
      @Param("like") String like,
      @Param("lang") String lang,
      Pageable pageable);

  // relevance 정렬(새 기본값): MATCH() 관련성 점수 내림차순, 발행 최신을 동점 타이브레이크로. 작가 핸들만 걸린 글은
  // 점수 0(본문 매칭 없음)이라 관련성 목록의 맨 아래로 밀리되 계속 노출된다 — 예전 검색이 잡던 것을 하나도 빠뜨리지
  // 않는다.
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "WHERE p.status = 'PUBLISHED' AND ("
              + "MATCH(p.search_text) AGAINST(:match IN BOOLEAN MODE) "
              + "OR p.user_id IN (SELECT u.id FROM users u "
              + "WHERE LOWER(u.username) LIKE :like ESCAPE '!' AND u.deleted_at IS NULL)) "
              + "AND (:lang IS NULL OR p.language_tag = :lang) "
              + "ORDER BY MATCH(p.search_text) AGAINST(:match IN BOOLEAN MODE) DESC, "
              + "p.published_at DESC")
  List<PostEntity> searchPublishedByRelevance(
      @Param("match") String match,
      @Param("like") String like,
      @Param("lang") String lang,
      Pageable pageable);

  // Same match, ranked by recent-window views (newest as tiebreak) so the trending sort means the
  // same thing inside search as on the main feed. Mirrors findPublishedTrendingSince: LEFT JOIN
  // post_view_event for the windowed COUNT. COUNT(DISTINCT e.id) so multiple view rows aggregate
  // correctly; GROUP BY p.id collapses them to one row per post.
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_view_event e ON e.post_id = p.id AND e.viewed_at >= :since "
              + "WHERE p.status = 'PUBLISHED' AND ("
              + "MATCH(p.search_text) AGAINST(:match IN BOOLEAN MODE) "
              + "OR p.user_id IN (SELECT u.id FROM users u "
              + "WHERE LOWER(u.username) LIKE :like ESCAPE '!' AND u.deleted_at IS NULL)) "
              + "AND (:lang IS NULL OR p.language_tag = :lang) "
              + "GROUP BY p.id "
              + "ORDER BY COUNT(DISTINCT e.id) DESC, p.published_at DESC")
  List<PostEntity> searchPublishedTrendingSince(
      @Param("match") String match,
      @Param("like") String like,
      @Param("since") Instant since,
      @Param("lang") String lang,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          "SELECT COUNT(*) FROM posts p "
              + "WHERE p.status = 'PUBLISHED' AND ("
              + "MATCH(p.search_text) AGAINST(:match IN BOOLEAN MODE) "
              + "OR p.user_id IN (SELECT u.id FROM users u "
              + "WHERE LOWER(u.username) LIKE :like ESCAPE '!' AND u.deleted_at IS NULL)) "
              + "AND (:lang IS NULL OR p.language_tag = :lang)")
  long countSearchPublished(
      @Param("match") String match, @Param("like") String like, @Param("lang") String lang);

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
