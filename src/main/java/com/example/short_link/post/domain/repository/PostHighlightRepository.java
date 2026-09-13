package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostHighlightEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostHighlightRepository {

  PostHighlightEntity save(PostHighlightEntity highlight);

  Optional<PostHighlightEntity> findById(Long id);

  List<PostHighlightEntity> findAllByIdIn(Collection<Long> ids);

  void delete(PostHighlightEntity highlight);

  List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId);

  List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<PostHighlightEntity> findByUserIdsOrderByCreatedAtDesc(
      Collection<Long> userIds, int page, int size);

  /** 발행된 글의 하이라이트만 최신순으로 반환한다. 초안·비공개 글의 인용은 제외한다. */
  List<PostHighlightEntity> findRecentOnPublishedPosts(int page, int size);

  int deleteAllByPostId(Long postId);
}
