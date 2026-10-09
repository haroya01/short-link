package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemType;

// A post is reached by its slug, a note by its id; a note's title is its excerpt.
public record SeriesItemPreview(
    String type, String slug, Long noteId, String title, String ogImageUrl) {

  static SeriesItemPreview of(SeriesEntry entry) {
    return entry.type() == SeriesItemType.POST
        ? new SeriesItemPreview(
            entry.type().name(), entry.slug(), null, entry.title(), entry.ogImageUrl())
        : new SeriesItemPreview(entry.type().name(), null, entry.refId(), entry.title(), null);
  }
}
