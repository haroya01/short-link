package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostBookmarkEntity;
import java.util.List;
import java.util.Optional;

public interface PostBookmarkRepository {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostBookmarkEntity> findByPostIdAndUserId(Long postId, Long userId);

  /** The user's bookmarks, newest first — drives the reading list. */
  List<PostBookmarkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  PostBookmarkEntity save(PostBookmarkEntity bookmark);

  void delete(PostBookmarkEntity bookmark);
}
