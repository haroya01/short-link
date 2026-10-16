package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.domain.PostEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record PublicPostListItem(
    Long id,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String thumbnailUrl,
    String languageTag,
    List<String> tags,
    long likeCount,
    Instant publishedAt,
    Instant lastEditedAt,
    boolean pinned,
    String audience,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean regionRestricted,
    @JsonInclude(JsonInclude.Include.NON_NULL) PostLockView locked,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean hasPassword,
    @JsonInclude(JsonInclude.Include.NON_NULL) String unlistedUrl,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> blockedCountries) {

  public PublicPostListItem(
      Long id,
      String slug,
      String title,
      String excerpt,
      String ogImageUrl,
      String thumbnailUrl,
      String languageTag,
      List<String> tags,
      long likeCount,
      Instant publishedAt,
      Instant lastEditedAt,
      boolean pinned) {
    this(
        id,
        slug,
        title,
        excerpt,
        ogImageUrl,
        thumbnailUrl,
        languageTag,
        tags,
        likeCount,
        publishedAt,
        lastEditedAt,
        pinned,
        PostAudience.PUBLIC.name(),
        null,
        null,
        null,
        null,
        null);
  }

  public static PublicPostListItem from(PostEntity post) {
    return of(post, PostAccess.FULL, null);
  }

  public static PublicPostListItem of(PostEntity post, PostAccess access, OwnerAudience owner) {
    boolean redacted = access.redacted();
    return new PublicPostListItem(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        redacted ? null : post.getExcerpt(),
        redacted ? null : post.getOgImageUrl(),
        redacted ? null : post.thumbnailUrl(),
        post.getLanguageTag(),
        redacted ? List.of() : List.copyOf(post.getTags()),
        redacted ? 0 : post.getLikeCount(),
        post.getPublishedAt(),
        redacted ? null : post.getLastEditedAt(),
        post.getPinOrder() != null,
        post.getAudience().name(),
        post.isRegionRestricted() ? Boolean.TRUE : null,
        PostLockView.of(access),
        owner == null ? null : owner.hasPassword(),
        owner == null ? null : owner.unlistedUrl(),
        owner == null ? null : owner.blockedCountries());
  }
}
