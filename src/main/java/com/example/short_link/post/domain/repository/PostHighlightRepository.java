package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostHighlightEntity;
import java.util.List;
import java.util.Optional;

public interface PostHighlightRepository {

  PostHighlightEntity save(PostHighlightEntity highlight);

  Optional<PostHighlightEntity> findById(Long id);

  void delete(PostHighlightEntity highlight);

  /** A post's highlights in reading order (block, then position within the block). */
  List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId);

  /** A reader's highlights across all posts, newest first — the "my highlights" library. */
  List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  /** Purge a post's highlights when it's permanently deleted. */
  int deleteAllByPostId(Long postId);
}
