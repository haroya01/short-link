package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;

public record PublicPostListItem(
    Long id,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String thumbnailUrl,
    boolean coverChosen,
    String languageTag,
    List<String> tags,
    long likeCount,
    Instant publishedAt,
    Instant lastEditedAt,
    boolean pinned) {

  public static PublicPostListItem from(PostEntity post) {
    return new PublicPostListItem(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.thumbnailUrl(),
        post.isCoverChosen(),
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getLikeCount(),
        post.getPublishedAt(),
        post.getLastEditedAt(),
        post.getPinOrder() != null);
  }
}
