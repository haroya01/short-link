package com.example.short_link.post.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

// Counts follow rows attributed through source_post_id. Unfollowed rows are gone, so counts
// represent retained follows, not historical acquisitions.
public interface PostFollowReader {

  long countBySourcePostId(Long postId);

  long countBySourcePostIdSince(Long postId, Instant since);

  long countByUserId(Long userId);

  long countByUserIdSince(Long userId, Instant since);

  Map<Long, Long> countBySourcePostIdIn(Collection<Long> postIds);
}
