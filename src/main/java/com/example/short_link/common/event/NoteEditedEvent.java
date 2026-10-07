package com.example.short_link.common.event;

// reachesElsewhere: the note answers a note or names someone, so the edit may owe servers beyond
// the writer's followers; federation checks which.
public record NoteEditedEvent(Long noteId, Long authorId, boolean reachesElsewhere) {

  public NoteEditedEvent(Long noteId, Long authorId) {
    this(noteId, authorId, false);
  }
}
