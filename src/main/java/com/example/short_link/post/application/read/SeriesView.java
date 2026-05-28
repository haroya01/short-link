package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.SeriesEntity;
import java.time.Instant;

public record SeriesView(
    Long id, String slug, String title, int postCount, Instant createdAt, Instant updatedAt) {

  public static SeriesView from(SeriesEntity series, int postCount) {
    return new SeriesView(
        series.getId(),
        series.getSlug(),
        series.getTitle(),
        postCount,
        series.getCreatedAt(),
        series.getUpdatedAt());
  }
}
