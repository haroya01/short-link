package com.example.short_link.post.domain;

import com.example.short_link.common.note.Excerpt;
import java.time.Instant;

public record SeriesNote(
    Long id, Long authorId, String body, String contentWarning, Instant createdAt, boolean shared) {

  public String excerpt() {
    return Excerpt.of(body, contentWarning);
  }
}
