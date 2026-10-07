package com.example.short_link.common.note;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// The note slice implements this so federation can render notes without importing that slice.
// A note whose author is soft-deleted does not resolve.
public interface NoteSnapshotReader {

  Optional<NoteSnapshot> find(Long noteId);

  long countByAuthor(Long authorId);

  List<Long> pinnedIds(Long authorId);

  record NoteSnapshot(
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
      Visibility visibility,
      Poll poll) {

    public NoteSnapshot(
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
        Visibility visibility) {
      this(
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
          null);
    }

    public NoteSnapshot(
        Long id,
        Long authorId,
        String authorUsername,
        String body,
        Instant createdAt,
        Instant editedAt,
        Long inReplyToId,
        Quote quote,
        List<Image> images) {
      this(
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
          false,
          Visibility.PUBLIC);
    }

    public NoteSnapshot(
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
      this(
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
          Visibility.PUBLIC);
    }
  }

  // Mirrors the note slice's visibility for readers outside it. Direct notes are never federated.
  enum Visibility {
    PUBLIC,
    UNLISTED,
    PRIVATE,
    DIRECT
  }

  record Quote(String title, String slug, String authorUsername) {}

  record QuotedNote(Long id, String authorUsername) {}

  record Image(String url, String contentType, String altText) {}

  record Poll(
      List<PollOption> options, Instant endTime, boolean multiple, long voters, boolean closed) {}

  record PollOption(String title, long votes) {}
}
