package com.example.short_link.blogimport.application.feed;

import java.util.List;

public record FeedDocument(
    String title, String link, String description, String language, List<FeedEntry> entries) {

  public FeedDocument {
    entries = entries == null ? List.of() : List.copyOf(entries);
  }

  public boolean isEmpty() {
    return title.isBlank() && link.isBlank() && entries.isEmpty();
  }
}
