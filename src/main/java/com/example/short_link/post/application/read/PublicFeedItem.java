package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

/** {@code followReason} is set only on the following feed; it is null on generic feeds. */
public record PublicFeedItem(
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
    FollowReason followReason,
    FeedSeriesRef series) {

  public PublicFeedItem(
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
      long likeCount) {
    this(
        id,
        author,
        slug,
        title,
        excerpt,
        ogImageUrl,
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
        languageTag,
        tags,
        publishedAt,
        viewCount,
        likeCount,
        followReason,
        ref);
  }
}
