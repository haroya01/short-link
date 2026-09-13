package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.TagCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository {

  Optional<PostEntity> findById(Long id);

  /** Serializes lifecycle decisions for a post until the caller's transaction completes. */
  Optional<PostEntity> findByIdForUpdate(Long id);

  List<PostEntity> findAllByIdIn(Collection<Long> ids);

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

  /** 조회수 변경에 사용할 최신 글을 배타적으로 잠근다. */
  Optional<PostEntity> findByUserIdAndSlugForUpdate(Long userId, String slug);

  /** Resolves a post by its share token (any status), for the unauthenticated preview read. */
  Optional<PostEntity> findByPreviewToken(String previewToken);

  PostEntity save(PostEntity post);

  /** 저장 시 생성되는 시각까지 확정한다. 쓰기 응답을 만들기 전에만 사용한다. */
  void flush();

  void delete(PostEntity post);

  /** Atomically bump the denormalized like counter so concurrent likes can't lose an update. */
  void incrementLikeCount(Long postId);

  /** Atomically drop the denormalized like counter, clamped at zero. */
  void decrementLikeCount(Long postId);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

  /** 분석 대상은 공개 이력이 있는 PUBLISHED/UNPUBLISHED 글이다. */
  List<PostEntity> findUserAnalyticsPosts(
      Long userId, int page, int size, PostPerformanceSort sort);

  long countUserAnalyticsPosts(Long userId);

  /** Includes {@code scheduledAt <= now}; the work list may become stale before publication. */
  List<Long> findScheduledDueIds(Instant now);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  /**
   * Locks existing members and requested posts together, in post-id order, before membership edits.
   */
  List<PostEntity> findSeriesMembersAndRequestedForUpdate(
      Long seriesId, Collection<Long> requestedIds);

  /** Locks an author's currently published posts in id order before replacing the pinned set. */
  List<PostEntity> findPublishedByUserIdForUpdate(Long userId);

  List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  /** 발행 최신순. page는 0부터, lang의 null·공백은 전체 언어다. */
  List<PostEntity> findPublishedRecent(String lang, int page, int size);

  /** 최근 구간 조회수 내림차순, 동률은 발행 최신순이다. 누적 조회수는 사용하지 않는다. 최근 조회가 없는 글도 포함하며 lang의 null·공백은 전체 언어다. */
  List<PostEntity> findPublishedTrending(String lang, int page, int size);

  long countPublished(String lang);

  long countPublishedByUserId(Long userId);

  /** Published posts carrying a tag (case-insensitive), newest first. */
  List<PostEntity> findPublishedByTag(String tag, int page, int size);

  long countPublishedByTag(String tag);

  /** 본문·메타데이터 또는 작성자 핸들을 검색해 관련성순으로 반환한다. lang의 null·공백은 전체 언어다. */
  List<PostEntity> searchPublishedByRelevance(String query, String lang, int page, int size);

  /** Same match as {@link #searchPublishedByRelevance} but newest first — the recent sort. */
  List<PostEntity> searchPublished(String query, String lang, int page, int size);

  /** Same match but ranked by recent-window view count — the trending sort. */
  List<PostEntity> searchPublishedTrending(String query, String lang, int page, int size);

  long countSearchPublished(String query, String lang);

  /** 작가·시리즈·소문자 태그 중 하나라도 일치하는 공개 글을 최신순으로 반환한다. */
  List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int page,
      int size);

  long countPublishedByAuthorsSeriesOrTags(
      Collection<Long> authorIds, Collection<Long> seriesIds, Collection<String> tags);

  /** 관심 태그가 있는 공개 글 중 본인 글과 읽은 글을 제외하고 최신순으로 반환한다. 태그는 소문자로 전달한다. */
  List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size);

  long countForYouCandidates(Long userId, Collection<String> tags, Collection<Long> excludeIds);

  List<TagCount> findPopularTags(int limit);

  List<AuthorPostStats> findTopAuthorStats(int limit);

  /** 발행 글이 minPosts개 이상인 시리즈를 최근 활동순으로 반환한다. */
  List<SeriesActivity> findActiveSeries(int minPosts, int limit);
}
