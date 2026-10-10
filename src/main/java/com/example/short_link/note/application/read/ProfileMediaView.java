package com.example.short_link.note.application.read;

import java.time.Instant;
import java.util.List;

public record ProfileMediaView(List<Item> items, int page, boolean hasNext) {

  // media is the note's first attachment; sensitive is set for a content warning too.
  public record Item(
      Long noteId,
      Instant createdAt,
      NoteView.Media media,
      int mediaCount,
      boolean sensitive,
      String contentWarning) {}
}
