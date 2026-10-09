package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.SeriesNote;
import java.time.Instant;

public record SeriesNoteView(
    Long id, String body, String contentWarning, String excerpt, Instant createdAt) {

  public static SeriesNoteView from(SeriesNote note) {
    return new SeriesNoteView(
        note.id(), note.body(), note.contentWarning(), note.excerpt(), note.createdAt());
  }
}
