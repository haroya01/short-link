package com.example.short_link.post.domain.repository;

import java.util.List;
import java.util.Map;

public interface CommentLikeRepository {

  /**
   * Returns 1 for a new like or 0 for a duplicate. Duplicate inserts must not fail the transaction.
   */
  int insertIgnore(Long commentId, Long userId);

  /** Returns the number of rows removed (0 or 1). */
  int deleteByCommentIdAndUserId(Long commentId, Long userId);

  long countByCommentId(Long commentId);

  Map<Long, Long> countByCommentIds(List<Long> commentIds);

  List<Long> findLikedCommentIds(Long userId, List<Long> commentIds);
}
