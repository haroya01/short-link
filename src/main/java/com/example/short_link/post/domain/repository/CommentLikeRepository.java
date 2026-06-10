package com.example.short_link.post.domain.repository;

import java.util.List;
import java.util.Map;

public interface CommentLikeRepository {

  /**
   * Insert a like, ignoring the duplicate-key conflict when the user already liked the comment.
   * Returns rows inserted (1 = newly liked, 0 = already liked) — idempotent without an exception
   * poisoning the surrounding transaction (same contract as the post-like repository).
   */
  int insertIgnore(Long commentId, Long userId);

  /** Delete the user's like if present; returns rows removed (0 or 1). */
  int deleteByCommentIdAndUserId(Long commentId, Long userId);

  long countByCommentId(Long commentId);

  /** Like counts for a batch of comments — one GROUP BY for the whole list render. */
  Map<Long, Long> countByCommentIds(List<Long> commentIds);

  /** Of the given comments, the ids the user has liked — drives likedByMe on the list. */
  List<Long> findLikedCommentIds(Long userId, List<Long> commentIds);
}
