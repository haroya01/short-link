package com.example.short_link.common.event;

import java.util.List;

// The note is gone by the time federation hears of it, so what it answered, the accounts
// elsewhere it named and whether it ever left this server travel with the event.
public record NoteDeletedEvent(
    Long noteId,
    Long authorId,
    List<String> mediaKeys,
    Long inReplyToId,
    List<String> remoteHandles,
    boolean federated) {

  public NoteDeletedEvent(
      Long noteId,
      Long authorId,
      List<String> mediaKeys,
      Long inReplyToId,
      List<String> remoteHandles) {
    this(noteId, authorId, mediaKeys, inReplyToId, remoteHandles, true);
  }

  public NoteDeletedEvent(Long noteId, Long authorId, List<String> mediaKeys) {
    this(noteId, authorId, mediaKeys, null, List.of());
  }
}
