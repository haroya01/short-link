package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLinkClick;
import java.time.Instant;
import java.util.List;

/**
 * Read port for kurl link-click attribution — how many clicks the links embedded in a post
 * (carrying {@code ?post=}) generated. Lives in the post module as a consumer port; the adapter
 * reads the link module's click_event log. This is the "글이 만든 클릭" differentiator that ties the blog
 * to the link product.
 */
public interface PostLinkClickReader {

  long countByPostId(Long postId);

  long countByPostIdSince(Long postId, Instant since);

  long countByUserId(Long userId);

  long countByUserIdSince(Long userId, Instant since);

  /** Per-link click breakdown for one post (most-clicked first), capped at {@code limit} links. */
  List<PostLinkClick> breakdownByPostId(Long postId, int limit);
}
