package com.example.short_link.common.event;

// reachesElsewhere: the note answers a note from another server or names someone there, and those
// people must hear of it even when the writer has no followers on their servers.
public record NotePublishedEvent(Long noteId, Long authorId, boolean reachesElsewhere) {

  public NotePublishedEvent(Long noteId, Long authorId) {
    this(noteId, authorId, false);
  }
}
