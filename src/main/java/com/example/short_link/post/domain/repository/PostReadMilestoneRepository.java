package com.example.short_link.post.domain.repository;

import java.time.Instant;

public interface PostReadMilestoneRepository {

  // True only for the one transaction that records this threshold for this post.
  boolean record(Long postId, int milestone, Instant reachedAt);
}
