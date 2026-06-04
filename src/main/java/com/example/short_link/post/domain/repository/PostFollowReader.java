package com.example.short_link.post.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Read port for follow attribution — how many follows a post drove ("이 글로 늘어난 팔로우"). Lives in the
 * post module as a consumer port; the adapter reads the user module's {@code user_follow} log by
 * {@code source_post_id}. Net semantics: a follow that was later undone left no row, so it isn't
 * counted. The cross-module seam is a single, read-only SQL lookup on source_post_id.
 */
public interface PostFollowReader {

  /** Follows attributed to one post, lifetime. */
  long countBySourcePostId(Long postId);

  /** Follows attributed to one post within the window. */
  long countBySourcePostIdSince(Long postId, Instant since);

  /** Follows attributed to any of this author's posts, lifetime. */
  long countByUserId(Long userId);

  /** Follows attributed to any of this author's posts within the window. */
  long countByUserIdSince(Long userId, Instant since);

  /**
   * Per-post follow counts for a set of posts in one query — {@code postId -> count} (0s omitted).
   */
  Map<Long, Long> countBySourcePostIdIn(Collection<Long> postIds);
}
