package com.example.short_link.post.presentation.response;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;

public record PostResponse(
    Long id,
    String slug,
    String title,
    String status,
    String languageTag,
    Instant publishedAt,
    Instant scheduledAt,
    String excerpt,
    String ogImageUrl,
    long viewCount,
    Instant createdAt,
    Instant updatedAt) {

  public static PostResponse from(PostEntity post) {
    return new PostResponse(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getStatus().name(),
        post.getLanguageTag(),
        post.getPublishedAt(),
        post.getScheduledAt(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getViewCount(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
