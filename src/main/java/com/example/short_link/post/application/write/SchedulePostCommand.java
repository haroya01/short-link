package com.example.short_link.post.application.write;

import java.time.Instant;

public record SchedulePostCommand(Long userId, Long postId, Instant scheduledAt) {

  public SchedulePostCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (scheduledAt == null) throw new IllegalArgumentException("scheduledAt required");
  }
}
