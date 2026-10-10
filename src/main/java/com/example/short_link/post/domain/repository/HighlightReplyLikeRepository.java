package com.example.short_link.post.domain.repository;

import java.util.Collection;
import java.util.Map;

public interface HighlightReplyLikeRepository {

  // Returns 1 for a new like or 0 for a duplicate. Duplicate inserts must not fail the transaction.
  int insertIgnore(Long replyId, Long userId);

  int deleteByReplyIdAndUserId(Long replyId, Long userId);

  long countByReplyId(Long replyId);

  // Replies nobody liked are absent from the map.
  Map<Long, Likes> likesOf(Collection<Long> replyIds, Long viewerId);

  record Likes(long count, boolean likedByViewer) {}
}
