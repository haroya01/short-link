package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostAudience;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record PublicFeedItem(
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
    FollowReason followReason,
    FeedSeriesRef series,
    String audience,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean regionRestricted,
    @JsonInclude(JsonInclude.Include.NON_NULL) PostLockView locked) {

  public PublicFeedItem(
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
      long likeCount) {
    this(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
        thumbnailUrl,
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        null,
        null,
        PostAudience.PUBLIC.name(),
        null,
        null);
  }

  public boolean redacted() {
    return locked != null || regionRestricted != null;
  }

  public PublicFeedItem withFollowReason(FollowReason reason) {
    FollowReason kept = redacted() && reason != null && reason.tag() != null ? null : reason;
    return new PublicFeedItem(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
        thumbnailUrl,
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        kept,
        series,
        audience,
        regionRestricted,
        locked);
  }

  public PublicFeedItem withSeries(FeedSeriesRef ref) {
    return new PublicFeedItem(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
        thumbnailUrl,
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        followReason,
        redacted() ? null : ref,
        audience,
        regionRestricted,
        locked);
  }
}
