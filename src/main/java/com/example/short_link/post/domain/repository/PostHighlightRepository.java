package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostHighlightEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostHighlightRepository {

  PostHighlightEntity save(PostHighlightEntity highlight);

  Optional<PostHighlightEntity> findById(Long id);

  /** Bulk fetch by ids — resolving highlights connected to a collection. */
  List<PostHighlightEntity> findAllByIdIn(Collection<Long> ids);

  void delete(PostHighlightEntity highlight);

  /** A post's highlights in reading order (block, then position within the block). */
  List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId);

  /** A reader's highlights across all posts, newest first — the "my highlights" library. */
  List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  /** Highlights by a set of users (e.g. curators the viewer follows), newest first — paged feed. */
  List<PostHighlightEntity> findByUserIdsOrderByCreatedAtDesc(
      Collection<Long> userIds, int page, int size);

  /**
   * 전역 공개 하이라이트 — *발행된* 글 위의 구절만 최신순, 페이지. 팔로우 그래프와 무관한 하이라이트 피드의 콜드스타트 폴백(scope=global 고정 포함)이 쓴다.
   * 초안·비공개 상태 글의 구절은 새지 않는다.
   */
  List<PostHighlightEntity> findRecentOnPublishedPosts(int page, int size);

  /** Purge a post's highlights when it's permanently deleted. */
  int deleteAllByPostId(Long postId);
}
