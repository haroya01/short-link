package com.example.short_link.post.application.read;

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
    FeedSeriesRef series) {

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
        null);
  }

  public PublicFeedItem withFollowReason(FollowReason reason) {
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
        reason,
        series);
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
        ref);
  }
}
