package com.example.short_link.post.application.read;

import java.time.Instant;

public record FeedSeriesNote(
    Long id,
    PublicAuthorView author,
    String body,
    String contentWarning,
    String excerpt,
    Instant createdAt,
    SeriesRef series) {

  public record SeriesRef(Long id, String slug, String title) {}
}
