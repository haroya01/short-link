package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;

public record PostView(
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
    List<String> tags,
    Instant createdAt,
    Instant updatedAt) {

  public static PostView from(PostEntity post) {
    return new PostView(
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
        List.copyOf(post.getTags()),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
