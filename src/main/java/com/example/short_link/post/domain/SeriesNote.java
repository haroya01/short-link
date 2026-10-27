package com.example.short_link.post.domain;

import com.example.short_link.common.note.Excerpt;
import java.time.Instant;

// A note as a series sees it: whose it is, whether everyone may read it, and enough to list it.
public record SeriesNote(
    Long id, Long authorId, String body, String contentWarning, Instant createdAt, boolean shared) {

  public String excerpt() {
    return Excerpt.of(body, contentWarning);
  }
}
