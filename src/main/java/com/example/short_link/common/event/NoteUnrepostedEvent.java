package com.example.short_link.common.event;

public record NoteUnrepostedEvent(Long repostId, Long noteId, Long userId, boolean remote) {

  public NoteUnrepostedEvent(Long repostId, Long noteId, Long userId) {
    this(repostId, noteId, userId, false);
  }
}
