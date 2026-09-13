package com.example.short_link.post.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Counts follow rows attributed through {@code source_post_id}. Unfollowed rows are gone, so counts
 * represent retained follows, not historical acquisitions.
 */
public interface PostFollowReader {

  long countBySourcePostId(Long postId);

  long countBySourcePostIdSince(Long postId, Instant since);

  /** Lifetime follows attributed to any post owned by this user. */
  long countByUserId(Long userId);

  long countByUserIdSince(Long userId, Instant since);

  /** Maps post ID to follow count; zero counts are omitted. */
  Map<Long, Long> countBySourcePostIdIn(Collection<Long> postIds);
}
