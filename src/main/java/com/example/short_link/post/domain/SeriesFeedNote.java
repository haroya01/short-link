package com.example.short_link.post.domain;

import java.time.Instant;

// A subscribed series' note as the subscription feed shows it, with its author and its series.
public record SeriesFeedNote(
    Long id,
    String body,
    String contentWarning,
    Instant createdAt,
    Author author,
    Long seriesId,
    String seriesSlug,
    String seriesTitle) {

  public record Author(
      Long id, String username, String bio, String avatarUrl, String displayName) {}

  public String excerpt() {
    return SeriesNote.excerptOf(body, contentWarning);
  }
}
