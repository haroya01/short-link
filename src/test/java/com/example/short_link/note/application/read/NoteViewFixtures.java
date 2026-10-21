package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import java.util.List;

public final class NoteViewFixtures {

  private NoteViewFixtures() {}

  public static NoteView note(
      Long id, String body, NoteAuthor author, Long inReplyToId, Long likeCount) {
    return new NoteView(
        id,
        body,
        null,
        null,
        likeCount,
        null,
        author,
        List.of(),
        null,
        inReplyToId,
        0,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        List.of(),
        null,
        false,
        false,
        "public",
        null,
        null,
        null,
        null,
        "everyone",
        null,
        false,
        null,
        null);
  }
}
