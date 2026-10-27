package com.example.short_link.common.note;

import java.time.Instant;
import java.util.List;

public final class RemoteNoteFixtures {

  private RemoteNoteFixtures() {}

  public static RemoteNotes.Revision revision(
      String body,
      String contentWarning,
      boolean sensitive,
      Instant editedAt,
      List<RemoteNotes.Media> media,
      String language) {
    return new RemoteNotes.Revision(
        body, contentWarning, sensitive, editedAt, media, language, RemoteNotes.Shape.NOTE, null);
  }
}
