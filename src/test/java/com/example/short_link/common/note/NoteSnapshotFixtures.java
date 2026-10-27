package com.example.short_link.common.note;

import com.example.short_link.common.note.NoteSnapshotReader.Image;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Quote;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import java.time.Instant;
import java.util.List;

public final class NoteSnapshotFixtures {

  private NoteSnapshotFixtures() {}

  public static NoteSnapshot note(
      Long id,
      Long authorId,
      String authorUsername,
      String body,
      Instant createdAt,
      Instant editedAt,
      Long inReplyToId,
      Quote quote,
      List<Image> images) {
    return note(
        id,
        authorId,
        authorUsername,
        body,
        createdAt,
        editedAt,
        inReplyToId,
        quote,
        images,
        null,
        null,
        false);
  }

  public static NoteSnapshot note(
      Long id,
      Long authorId,
      String authorUsername,
      String body,
      Instant createdAt,
      Instant editedAt,
      Long inReplyToId,
      Quote quote,
      List<Image> images,
      QuotedNote quotedNote,
      String contentWarning,
      boolean sensitive) {
    return note(
        id,
        authorId,
        authorUsername,
        body,
        createdAt,
        editedAt,
        inReplyToId,
        quote,
        images,
        quotedNote,
        contentWarning,
        sensitive,
        NoteVisibility.DEFAULT);
  }

  public static NoteSnapshot note(
      Long id,
      Long authorId,
      String authorUsername,
      String body,
      Instant createdAt,
      Instant editedAt,
      Long inReplyToId,
      Quote quote,
      List<Image> images,
      QuotedNote quotedNote,
      String contentWarning,
      boolean sensitive,
      NoteVisibility visibility) {
    return new NoteSnapshot(
        id,
        authorId,
        authorUsername,
        body,
        createdAt,
        editedAt,
        inReplyToId,
        quote,
        images,
        quotedNote,
        contentWarning,
        sensitive,
        visibility,
        null,
        null);
  }
}
