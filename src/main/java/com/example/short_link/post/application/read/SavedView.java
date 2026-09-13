package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

/** {@code folderId == null} means unfiled. Fields match the flat SavedPost client shape. */
public record SavedView(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String languageTag,
    List<String> tags,
    Instant publishedAt,
    long viewCount,
    long likeCount,
    Long folderId) {

  public static SavedView of(PublicFeedItem item, Long folderId) {
    return new SavedView(
        item.id(),
        item.author(),
        item.slug(),
        item.title(),
        item.excerpt(),
        item.ogImageUrl(),
        item.languageTag(),
        item.tags(),
        item.publishedAt(),
        item.viewCount(),
        item.likeCount(),
        folderId);
  }
}
