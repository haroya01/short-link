package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostRepository extends JpaRepository<PostEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PostEntity p where p.id = :id")
  Optional<PostEntity> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PostEntity p where p.userId = :userId and p.slug = :slug")
  Optional<PostEntity> findByUserIdAndSlugForUpdate(
      @Param("userId") Long userId, @Param("slug") String slug);

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

  // 지표가 같아도 페이지 순서가 안정되도록 어댑터에서 ID 내림차순을 추가한다.
  List<PostEntity> findByUserIdAndStatusIn(
      Long userId, Collection<PostStatus> statuses, Pageable pageable);

  long countByUserIdAndStatusIn(Long userId, Collection<PostStatus> statuses);

  // 공개 프로필의 블로그 표시에는 UNPUBLISHED를 포함하는 분석용 카운트를 쓰면 안 된다.
  long countByUserIdAndStatus(Long userId, PostStatus status);

  @Query(
      "select p.id from PostEntity p where p.status = :status and p.scheduledAt <= :now order by p.id")
  List<Long> findScheduledDueIds(@Param("status") PostStatus status, @Param("now") Instant now);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from PostEntity p where p.seriesId = :seriesId or p.id in :requestedIds order by p.id")
  List<PostEntity> findSeriesMembersAndRequestedForUpdate(
      @Param("seriesId") Long seriesId, @Param("requestedIds") Collection<Long> requestedIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PostEntity p where p.userId = :userId and p.status = :status order by p.id")
  List<PostEntity> findPublishedByUserIdForUpdate(
      @Param("userId") Long userId, @Param("status") PostStatus status);

  List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  // LEFT JOIN으로 최근 조회가 없는 글도 포함한다. 누적 view_count는 순위에 사용하지 않는다.
  // MySQL에서는 기본키로 GROUP BY하면 p.*를 선택할 수 있다.
  String RECENT_HUMAN_VIEWS =
      "LEFT JOIN post_view_event e ON e.post_id = p.id AND e.viewed_at >= :since "
          + "AND e.is_bot = FALSE AND e.visitor_hash IS NOT NULL ";

  String TRENDING_ORDER =
      "GROUP BY p.id ORDER BY COUNT(DISTINCT e.visitor_hash) DESC, p.published_at DESC";

  String BROWSE_PREDICATE =
      "WHERE p.status = 'PUBLISHED' AND (:lang IS NULL OR p.language_tag = :lang) "
          + "AND p.body_text_length >= :minBody"
          + HeardSql.POST_AUTHOR
          + " AND (p.series_id IS NULL OR NOT EXISTS (SELECT 1 FROM posts q "
          + "WHERE q.series_id = p.series_id AND q.status = 'PUBLISHED' "
          + "AND q.body_text_length >= :minBody "
          + "AND (:lang IS NULL OR q.language_tag = :lang) "
          + "AND (q.published_at > p.published_at "
          + "OR (q.published_at = p.published_at AND q.id > p.id)))) ";

  @Query(
      nativeQuery = true,
      value = "SELECT p.* FROM posts p " + RECENT_HUMAN_VIEWS + BROWSE_PREDICATE + TRENDING_ORDER)
  List<PostEntity> findPublishedTrendingSince(
      @Param("since") Instant since,
      @Param("lang") String lang,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value = "SELECT p.* FROM posts p " + BROWSE_PREDICATE + "ORDER BY p.published_at DESC")
  List<PostEntity> findPublishedRecent(
      @Param("lang") String lang,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM posts p " + BROWSE_PREDICATE)
  long countPublishedByLang(
      @Param("lang") String lang,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now);

  String TAGGED = "JOIN post_tag t ON t.post_id = p.id ";

  String TAG_PREDICATE =
      "WHERE LOWER(t.tag) = LOWER(:tag) AND p.status = 'PUBLISHED' "
          + "AND p.body_text_length >= :minBody"
          + HeardSql.POST_AUTHOR
          + " ";

  @Query(
      nativeQuery = true,
      value = "SELECT p.* FROM posts p " + TAGGED + TAG_PREDICATE + "ORDER BY p.published_at DESC")
  List<PostEntity> findPublishedByTag(
      @Param("tag") String tag,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p " + TAGGED + RECENT_HUMAN_VIEWS + TAG_PREDICATE + TRENDING_ORDER)
  List<PostEntity> findPublishedTrendingByTagSince(
      @Param("tag") String tag,
      @Param("since") Instant since,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM posts p " + TAGGED + TAG_PREDICATE)
  long countPublishedByTag(
      @Param("tag") String tag,
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now);

  // 태그가 없는 작가·시리즈 글도 포함하려고 LEFT JOIN한다. DISTINCT는 여러 태그의 중복을 제거한다.
  // 빈 IN 인수는 어댑터가 매칭되지 않는 값으로 변환한다.
  String FOLLOWED_PREDICATE =
      "LEFT JOIN post_tag t ON t.post_id = p.id "
          + "WHERE p.status = 'PUBLISHED' "
          + "AND (p.user_id IN (:authorIds) OR p.series_id IN (:seriesIds) "
          + "OR LOWER(t.tag) IN (:tags))"
          + HeardSql.POST_AUTHOR
          + " ";

  @Query(
      nativeQuery = true,
      value =
          "SELECT DISTINCT p.* FROM posts p " + FOLLOWED_PREDICATE + "ORDER BY p.published_at DESC")
  List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("tags") Collection<String> tags,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value = "SELECT COUNT(DISTINCT p.id) FROM posts p " + FOLLOWED_PREDICATE)
  long countPublishedByAuthorsSeriesOrTags(
      @Param("authorIds") Collection<Long> authorIds,
      @Param("seriesIds") Collection<Long> seriesIds,
      @Param("tags") Collection<String> tags,
      @Param("viewer") long viewer,
      @Param("now") Instant now);

  // DISTINCT로 다중 태그의 중복을 제거한다. 빈 NOT IN 인수는 어댑터가 변환한다.
  String FOR_YOU_PREDICATE =
      "JOIN post_tag t ON t.post_id = p.id "
          + "WHERE p.status = 'PUBLISHED' AND p.user_id <> :viewer "
          + "AND LOWER(t.tag) IN (:tags) AND p.id NOT IN (:excludeIds) "
          + "AND p.body_text_length >= :minBody"
          + HeardSql.POST_AUTHOR
          + " ";

  @Query(
      nativeQuery = true,
      value =
          "SELECT DISTINCT p.* FROM posts p " + FOR_YOU_PREDICATE + "ORDER BY p.published_at DESC")
  List<PostEntity> findForYouCandidates(
      @Param("viewer") long viewer,
      @Param("tags") Collection<String> tags,
      @Param("excludeIds") Collection<Long> excludeIds,
      @Param("minBody") int minBody,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value = "SELECT COUNT(DISTINCT p.id) FROM posts p " + FOR_YOU_PREDICATE)
  long countForYouCandidates(
      @Param("viewer") long viewer,
      @Param("tags") Collection<String> tags,
      @Param("excludeIds") Collection<Long> excludeIds,
      @Param("minBody") int minBody,
      @Param("now") Instant now);

  @Query(
      "select t, count(p) from PostEntity p join p.tags t "
          + "where p.status = :status and p.bodyTextLength >= :minBody "
          + "group by t order by count(p) desc")
  List<Object[]> findPopularTags(
      @Param("status") PostStatus status, @Param("minBody") int minBody, Pageable pageable);

  // 검색 평문이 없는 글도 작성자 핸들로 찾을 수 있도록 LEFT JOIN한다.
  // :match는 연산자를 제거한 BOOLEAN 검색어, :like는 이스케이프한 핸들 검색어다.
  // :titleLike는 ngram 색인에 남는 항이 없는 질의(두 글자 미만·스톱워드)의 제목·요약 폴백에만 사용한다.
  // :titleWord는 그 폴백이 영문·숫자 낱말 속(email의 ai)에 걸리지 않게 하는 경계 정규식이다.
  // ngram에서 접두·구문 연산자는 한글 다중 바이그램을 깨뜨리고 NATURAL MODE는 일부 바이그램만
  // 겹쳐도 매칭되므로, 연산자 없는 BOOLEAN 항을 유지한다.
  String SEARCH_PREDICATE =
      "WHERE p.status = 'PUBLISHED' AND ("
          + "MATCH(s.search_text) AGAINST(:match IN BOOLEAN MODE) "
          + "OR p.user_id IN (SELECT u.id FROM users u "
          + "WHERE LOWER(u.username) LIKE :like ESCAPE '!' AND u.deleted_at IS NULL) "
          + "OR (:titleLike IS NOT NULL AND (LOWER(p.title) LIKE :titleLike ESCAPE '!' "
          + "OR LOWER(COALESCE(p.excerpt, '')) LIKE :titleLike ESCAPE '!') "
          + "AND (:titleWord IS NULL "
          + "OR REGEXP_LIKE(LOWER(CONCAT(p.title, ' ', COALESCE(p.excerpt, ''))), :titleWord)))) "
          + "AND (:lang IS NULL OR p.language_tag = :lang)"
          + HeardSql.POST_AUTHOR
          + " ";

  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_search_text s ON s.post_id = p.id "
              + SEARCH_PREDICATE
              + "ORDER BY p.published_at DESC")
  List<PostEntity> searchPublishedRecent(
      @Param("match") String match,
      @Param("like") String like,
      @Param("titleLike") String titleLike,
      @Param("titleWord") String titleWord,
      @Param("lang") String lang,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  // 핸들·제목·요약 폴백으로만 매칭된 글은 관련성 점수가 0이어도 결과에 포함한다.
  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_search_text s ON s.post_id = p.id "
              + SEARCH_PREDICATE
              + "ORDER BY MATCH(s.search_text) AGAINST(:match IN BOOLEAN MODE) DESC, "
              + "p.published_at DESC")
  List<PostEntity> searchPublishedByRelevance(
      @Param("match") String match,
      @Param("like") String like,
      @Param("titleLike") String titleLike,
      @Param("titleWord") String titleWord,
      @Param("lang") String lang,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM posts p "
              + "LEFT JOIN post_search_text s ON s.post_id = p.id "
              + RECENT_HUMAN_VIEWS
              + SEARCH_PREDICATE
              + TRENDING_ORDER)
  List<PostEntity> searchPublishedTrendingSince(
      @Param("match") String match,
      @Param("like") String like,
      @Param("titleLike") String titleLike,
      @Param("titleWord") String titleWord,
      @Param("since") Instant since,
      @Param("lang") String lang,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          "SELECT COUNT(*) FROM posts p "
              + "LEFT JOIN post_search_text s ON s.post_id = p.id "
              + SEARCH_PREDICATE)
  long countSearchPublished(
      @Param("match") String match,
      @Param("like") String like,
      @Param("titleLike") String titleLike,
      @Param("titleWord") String titleWord,
      @Param("lang") String lang,
      @Param("viewer") long viewer,
      @Param("now") Instant now);

  @Query(
      nativeQuery = true,
      value =
          "SELECT p.user_id, COUNT(*), COALESCE(SUM(p.view_count), 0) FROM posts p "
              + "WHERE p.status = 'PUBLISHED' AND p.body_text_length >= :minBody"
              + HeardSql.POST_AUTHOR
              + " GROUP BY p.user_id "
              + "ORDER BY COUNT(*) DESC, COALESCE(SUM(p.view_count), 0) DESC")
  List<Object[]> findTopAuthorIds(
      @Param("minBody") int minBody,
      @Param("viewer") long viewer,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(
      nativeQuery = true,
      value =
          "SELECT p.* FROM post_note_quote q JOIN posts p ON p.id = q.post_id "
              + "WHERE q.note_id = :noteId AND p.status = 'PUBLISHED' "
              + "ORDER BY p.published_at DESC, p.id DESC LIMIT :limit OFFSET :offset")
  List<PostEntity> findPublishedQuotingNote(
      @Param("noteId") Long noteId, @Param("offset") int offset, @Param("limit") int limit);
}
