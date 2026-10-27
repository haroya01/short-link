package com.example.short_link.common.note;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// The note slice implements this so federation can render notes without importing that slice.
// A note whose author is soft-deleted does not resolve.
public interface NoteSnapshotReader {

  Optional<NoteSnapshot> find(Long noteId);

  long countForFollowersByAuthor(Long authorId);

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
      NoteVisibility visibility,
      Poll poll,
      String language) {}

  record Quote(String title, String slug, String authorUsername) {}

  record QuotedNote(Long id, String authorUsername, String uri, String url) {

    public QuotedNote(Long id, String authorUsername) {
      this(id, authorUsername, null, null);
    }

    public boolean remote() {
      return uri != null;
    }
  }

  record Image(String url, String altText, String contentType, Integer width, Integer height) {}

  record Poll(
      List<PollOption> options, Instant endTime, boolean multiple, long voters, boolean closed) {}

  record PollOption(String title, long votes) {}
}
