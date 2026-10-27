package com.example.short_link.post.domain.repository;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.TagCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PostRepository {

  Optional<PostEntity> findById(Long id);

  Optional<PostEntity> findByIdForUpdate(Long id);

  List<PostEntity> findAllByIdIn(Collection<Long> ids);

  // Empty also when the viewer blocked the author or the author blocked the viewer.
  Optional<PostEntity> findUnblockedByUserIdAndSlug(Long userId, String slug, Long viewerId);

  Optional<PostEntity> findByUserIdAndSlugForUpdate(Long userId, String slug);

  Optional<PostEntity> findByPreviewToken(String previewToken);

  PostEntity save(PostEntity post);

  // 저장 시 생성되는 시각까지 확정한다. 쓰기 응답을 만들기 전에만 사용한다.
  void flush();

  void delete(PostEntity post);

  // Atomically bump the denormalized like counter so concurrent likes can't lose an update.
  void incrementLikeCount(Long postId);

  void decrementLikeCount(Long postId);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  // An author's published posts a profile list may carry for this viewer, newest first: public,
  // followers-only and password posts, unlisted ones too for the author, and never room posts.
  List<PostEntity> findProfilePosts(Long userId, Long viewerId);

  List<PostEntity> findUserAnalyticsPosts(
      Long userId, int page, int size, PostPerformanceSort sort);

  long countUserAnalyticsPosts(Long userId);

  // Includes scheduledAt <= now; the work list may become stale before publication.
  List<Long> findScheduledDueIds(Instant now);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  // Locks existing members and requested posts together, in post-id order, before membership edits.
  List<PostEntity> findSeriesMembersAndRequestedForUpdate(
      Long seriesId, Collection<Long> requestedIds);

  // Locks an author's currently published posts in id order before replacing the pinned set.
  List<PostEntity> findPublishedByUserIdForUpdate(Long userId);

  List<PostEntity> findAllBySeriesIdInOrderBySeriesOrderAsc(Collection<Long> seriesIds);

  List<PostEntity> findOpenBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  // A viewer-scoped read leaves out authors the viewer blocked, who blocked the viewer, or whom the
  // viewer muted until the mute ends; a null viewer is anonymous.
  List<PostEntity> findPublishedRecent(Long viewerId, String lang, int page, int size);

  List<PostEntity> findPublishedTrending(Long viewerId, String lang, int page, int size);

  // The home's latest: what discovery lists, and the room posts of the viewer's rooms they keep in
  // their feed.
  List<PostEntity> findHomeRecentAt(Long viewerId, int offset, int limit, Unseen unseen);

  record PoolRow(
      Long id,
      Long userId,
      String language,
      Instant publishedAt,
      long likes,
      long quotes,
      long highlights,
      List<String> tags) {}

  // What the home's latest lists outside rooms, published between since and until, newest first.
  // Quotes count the public or unlisted notes quoting the post.
  List<PoolRow> findHomePool(Long viewerId, Instant since, Instant until, int limit);

  // What the home's latest lists outside rooms, newest published first, the later id first among
  // posts published at the same time. Below takes those after the position (publishedAt, id),
  // above those before it, nearest first; a null position starts from the top.
  List<PostEntity> findBlogBelow(Long viewerId, Instant publishedAt, Long id, int limit);

  List<PostEntity> findBlogAbove(Long viewerId, Instant publishedAt, long id, int limit);

  List<PostEntity> findInRoomAt(Long roomId, Long viewerId, int offset, int limit);

  List<PostEntity> findInRoomsOfAt(Long viewerId, int offset, int limit);

  record CardMarks(long comments, boolean liked) {}

  Map<Long, CardMarks> cardMarks(Collection<Long> postIds, Long viewerId);

  List<PostEntity> findPublishedTrendingAt(Long viewerId, int offset, int limit);

  // The home's following: what the viewer follows, and the room posts of their feed rooms.
  List<PostEntity> findHomeFollowingAt(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int offset,
      int limit);

  long countPublished(Long viewerId, String lang);

  long countPublishedByUserId(Long userId);

  List<PostEntity> findPublishedByTag(Long viewerId, String tag, int page, int size);

  List<PostEntity> findPublishedTrendingByTag(Long viewerId, String tag, int page, int size);

  long countPublishedByTag(Long viewerId, String tag);

  List<PostEntity> findPublishedQuotingNote(Long noteId, Long viewerId, int offset, int limit);

  List<PostEntity> searchPublishedByRelevanceAt(
      Long viewerId, String query, String lang, long offset, int limit);

  List<PostEntity> searchPublishedAt(
      Long viewerId, String query, String lang, long offset, int limit);

  List<PostEntity> searchPublishedTrendingAt(
      Long viewerId, String query, String lang, long offset, int limit);

  List<PostEntity> findPublishedByAuthorsSeriesOrTags(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int page,
      int size);

  long countPublishedByAuthorsSeriesOrTags(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags);

  List<PostEntity> findForYouCandidates(
      Long userId, Collection<String> tags, Collection<Long> excludeIds, int page, int size);

  long countForYouCandidates(Long userId, Collection<String> tags, Collection<Long> excludeIds);

  List<TagCount> findPopularTags(int limit);

  List<AuthorPostStats> findTopAuthorStats(Long viewerId, int limit);
}
