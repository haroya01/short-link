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

  List<PostEntity> findAllByIdIn(Collection<Long> ids);

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

  PostEntity save(PostEntity post);

  void delete(PostEntity post);

  /** Atomically bump the denormalized like counter so concurrent likes can't lose an update. */
  void incrementLikeCount(Long postId);

  /** Atomically drop the denormalized like counter, clamped at zero. */
  void decrementLikeCount(Long postId);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

  /**
   * Author's posts that have been public (PUBLISHED/UNPUBLISHED), paged + sorted for the analytics
   * per-post table (infinite scroll).
   */
  List<PostEntity> findUserAnalyticsPosts(
      Long userId, int page, int size, PostPerformanceSort sort);

  long countUserAnalyticsPosts(Long userId);

  /** SCHEDULED posts whose scheduledAt has arrived (<= now) — the auto-publish job's work list. */
  List<PostEntity> findScheduledDue(Instant now);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  /**
   * All member posts across many series in one query — the batch form of {@link
   * #findAllBySeriesIdOrderBySeriesOrderAsc} for the series-analytics list (group by seriesId in
   * memory). Series order is preserved within each series.
   */
  List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  /** Global public feed (all authors), newest first. 0-based page. */
  List<PostEntity> findPublishedRecent(int page, int size);

  /**
   * Global public feed ranked by views inside a recent window (recent traction), newest as tiebreak
   * — the honest "trending" sort. Posts with no recent views fall back to recency so the feed stays
   * full. Distinct from posts.view_count, the lifetime counter shown on cards.
   */
  List<PostEntity> findPublishedTrending(int page, int size);

  long countPublished();

  /** Count of an author's PUBLISHED posts — backs the public profile's blog entry-point flag. */
  long countPublishedByUserId(Long userId);

  /** Published posts carrying a tag (case-insensitive), newest first. */
  List<PostEntity> findPublishedByTag(String tag, int page, int size);

  long countPublishedByTag(String tag);

  /** Published posts matching free text in title / excerpt / tags / author handle, newest first. */
  List<PostEntity> searchPublished(String query, int page, int size);

  /** Same match as {@link #searchPublished} but ranked by view count — the trending sort. */
  List<PostEntity> searchPublishedTrending(String query, int page, int size);

  long countSearchPublished(String query);

  /**
   * Published posts by any of the given authors OR in any of the given series, newest first — the
   * "following" feed once it merges followed authors with subscribed series. Pass non-empty
   * collections (use a sentinel that matches nothing when a side is empty).
   */
  List<PostEntity> findPublishedByAuthorIdsOrSeriesIds(
      Collection<Long> authorIds, Collection<Long> seriesIds, int page, int size);

  long countPublishedByAuthorIdsOrSeriesIds(Collection<Long> authorIds, Collection<Long> seriesIds);

  /** Most-used tags across published posts, most popular first — the 주제 index. */
  List<TagCount> findPopularTags(int limit);

  /**
   * [authorId, publishedPostCount, totalViews] ranked for the discovery rail, top authors first.
   */
  List<AuthorPostStats> findTopAuthorStats(int limit);

  /**
   * Series with at least {@code minPosts} published members, most recently active first — backs the
   * cross-author series discovery surface (the feed's series cards).
   */
  List<SeriesActivity> findActiveSeries(int minPosts, int limit);
}
