package com.example.short_link.post.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record SavedView(
    long id,
    PublicAuthorView author,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String thumbnailUrl,
    String languageTag,
    List<String> tags,
    Instant publishedAt,
    long viewCount,
    long likeCount,
    Long folderId,
    String audience,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean regionRestricted,
    @JsonInclude(JsonInclude.Include.NON_NULL) PostLockView locked) {

  public static SavedView of(PublicFeedItem item, Long folderId) {
    return new SavedView(
        item.id(),
        item.author(),
        item.slug(),
        item.title(),
        item.excerpt(),
        item.ogImageUrl(),
        item.thumbnailUrl(),
        item.languageTag(),
        item.tags(),
        item.publishedAt(),
        item.viewCount(),
        item.likeCount(),
        folderId,
        item.audience(),
        item.regionRestricted(),
        item.locked());
  }
}
