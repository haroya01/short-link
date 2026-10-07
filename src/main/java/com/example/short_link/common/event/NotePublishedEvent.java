package com.example.short_link.common.event;

// replyToRemote: the note answers a note from another server, whose author must hear of it even
// when the writer has no followers there.
public record NotePublishedEvent(Long noteId, Long authorId, boolean replyToRemote) {

  public NotePublishedEvent(Long noteId, Long authorId) {
    this(noteId, authorId, false);
  }
}
