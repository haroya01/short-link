package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLikeEntity;
import java.util.List;

public interface PostLikeRepository {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  /** The user's likes, newest first — drives the "liked posts" list. */
  List<PostLikeEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  long countByPostId(Long postId);

  /**
   * Insert a like, ignoring the duplicate-key conflict when the user already liked the post.
   * Returns the number of rows inserted (1 = newly liked, 0 = already liked) so the caller can
   * adjust the denormalized counter exactly once. Atomic — no read-then-write race, and no
   * exception to poison the surrounding transaction.
   */
  int insertIgnore(Long postId, Long userId);

  /** Delete the user's like if present; returns the number of rows removed (0 or 1). */
  int deleteByPostIdAndUserId(Long postId, Long userId);
}
