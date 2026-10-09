package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostPerformanceSort;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.TagCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository {

  Optional<PostEntity> findById(Long id);

  Optional<PostEntity> findByIdForUpdate(Long id);

  List<PostEntity> findAllByIdIn(Collection<Long> ids);

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

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

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

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

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  // A viewer-scoped read leaves out authors the viewer blocked, who blocked the viewer, or whom the
  // viewer muted until the mute ends; a null viewer is anonymous.
  List<PostEntity> findPublishedRecent(Long viewerId, String lang, int page, int size);

  List<PostEntity> findPublishedTrending(Long viewerId, String lang, int page, int size);

  long countPublished(Long viewerId, String lang);

  long countPublishedByUserId(Long userId);

  List<PostEntity> findPublishedByTag(Long viewerId, String tag, int page, int size);

  List<PostEntity> findPublishedTrendingByTag(Long viewerId, String tag, int page, int size);

  long countPublishedByTag(Long viewerId, String tag);

  List<PostEntity> findPublishedQuotingNote(Long noteId, int offset, int limit);

  List<PostEntity> searchPublishedByRelevance(
      Long viewerId, String query, String lang, int page, int size);

  List<PostEntity> searchPublished(Long viewerId, String query, String lang, int page, int size);

  List<PostEntity> searchPublishedTrending(
      Long viewerId, String query, String lang, int page, int size);

  long countSearchPublished(Long viewerId, String query, String lang);

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
