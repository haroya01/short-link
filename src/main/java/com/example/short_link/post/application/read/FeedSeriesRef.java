package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.SeriesSummary;

public record FeedSeriesRef(String slug, String title, int postCount) {

  static FeedSeriesRef from(SeriesSummary summary) {
    return new FeedSeriesRef(summary.slug(), summary.title(), (int) summary.publishedCount());
  }
}
