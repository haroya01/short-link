package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.TagCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostRepository {

  Optional<PostEntity> findById(Long id);

  Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug);

  PostEntity save(PostEntity post);

  void delete(PostEntity post);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(Long userId, PostStatus status);

  List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId);

  List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status);

  /** Global public feed (all authors), newest first. 0-based page. */
  List<PostEntity> findPublishedRecent(int page, int size);

  /** Global public feed ranked by view count, newest as tiebreak. */
  List<PostEntity> findPublishedTrending(int page, int size);

  long countPublished();

  /** Published posts carrying a tag (case-insensitive), newest first. */
  List<PostEntity> findPublishedByTag(String tag, int page, int size);

  long countPublishedByTag(String tag);

  /** Published posts by any of the given authors, newest first — the "following" feed. */
  List<PostEntity> findPublishedByAuthorIds(Collection<Long> authorIds, int page, int size);

  long countPublishedByAuthorIds(Collection<Long> authorIds);

  /** Most-used tags across published posts, most popular first — the 주제 index. */
  List<TagCount> findPopularTags(int limit);
}
