package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;

/**
 * Exposes the stable post ID for typed references such as abuse reports; omits private lifecycle
 * fields.
 */
public record PublicPostListItem(
    Long id,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
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
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getLikeCount(),
        post.getPublishedAt(),
        post.getLastEditedAt(),
        post.getPinOrder() != null);
  }
}
