package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.List;

public interface PostLikeRepository {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  long countByPostId(Long postId);

  /**
   * Returns 1 for a new like or 0 for a duplicate, allowing the counter to change exactly once.
   * Duplicate inserts must not fail the transaction.
   */
  int insertIgnore(Long postId, Long userId);

  /** Returns the number of rows removed (0 or 1). */
  int deleteByPostIdAndUserId(Long postId, Long userId);

  int deleteAllByPostId(Long postId);
}
