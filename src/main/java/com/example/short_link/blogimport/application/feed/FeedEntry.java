package com.example.short_link.blogimport.application.feed;

import java.time.Instant;
import java.util.List;

public record FeedEntry(
    String title, String link, String guid, Instant publishedAt, String html, List<String> tags) {

  public FeedEntry {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
