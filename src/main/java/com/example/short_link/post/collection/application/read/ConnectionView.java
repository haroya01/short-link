package com.example.short_link.post.collection.application.read;

import com.example.short_link.common.note.NoteBlock;
import java.time.Instant;

public record ConnectionView(
    Long id,
    String blockType,
    String why,
    Instant connectedAt,
    String title,
    String excerpt,
    String slug,
    String username,
    String quote,
    String body,
    Long noteId) {

  public static ConnectionView post(
      Long id, String why, Instant at, String title, String excerpt, String slug, String username) {
    return new ConnectionView(
        id, "POST", why, at, title, excerpt, slug, username, null, null, null);
  }

  public static ConnectionView highlight(
      Long id,
      String why,
      Instant at,
      String quote,
      String postTitle,
      String slug,
      String username) {
    return new ConnectionView(
        id, "HIGHLIGHT", why, at, postTitle, null, slug, username, quote, null, null);
  }

  public static ConnectionView note(Long id, String why, Instant at, NoteBlock note) {
    return new ConnectionView(
        id, "NOTE", why, at, null, null, null, note.authorUsername(), null, note.body(), note.id());
  }
}
