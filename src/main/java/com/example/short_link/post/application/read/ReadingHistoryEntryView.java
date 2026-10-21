package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostAudience;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record ReadingHistoryEntryView(
    Long postId,
    Long userId,
    String username,
    String avatarUrl,
    String title,
    String slug,
    String excerpt,
    String ogImageUrl,
    String thumbnailUrl,
    Instant readAt,
    String audience,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean regionRestricted,
    @JsonInclude(JsonInclude.Include.NON_NULL) PostLockView locked) {

  public ReadingHistoryEntryView(
      Long postId,
      Long userId,
      String username,
      String avatarUrl,
      String title,
      String slug,
      String excerpt,
      String ogImageUrl,
      String thumbnailUrl,
      Instant readAt) {
    this(
        postId,
        userId,
        username,
        avatarUrl,
        title,
        slug,
        excerpt,
        ogImageUrl,
        thumbnailUrl,
        readAt,
        PostAudience.DEFAULT.name(),
        null,
        null);
  }
}
