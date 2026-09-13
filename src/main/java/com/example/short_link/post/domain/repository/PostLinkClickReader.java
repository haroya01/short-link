package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostLinkClick;
import java.time.Instant;
import java.util.List;

/** Reads click_event attribution supplied by links carrying {@code ?post=}. */
public interface PostLinkClickReader {

  long countByPostId(Long postId);

  long countByPostIdSince(Long postId, Instant since);

  long countByUserId(Long userId);

  long countByUserIdSince(Long userId, Instant since);

  /** Per-link click breakdown for one post (most-clicked first), capped at {@code limit} links. */
  List<PostLinkClick> breakdownByPostId(Long postId, int limit);
}
