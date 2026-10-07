package com.example.short_link.common.event;

import java.util.List;

// The note is gone by the time federation hears of it, so what it answered and the accounts
// elsewhere it named travel with the event.
public record NoteDeletedEvent(
    Long noteId,
    Long authorId,
    List<String> mediaKeys,
    Long inReplyToId,
    List<String> remoteHandles) {

  public NoteDeletedEvent(Long noteId, Long authorId, List<String> mediaKeys) {
    this(noteId, authorId, mediaKeys, null, List.of());
  }
}
