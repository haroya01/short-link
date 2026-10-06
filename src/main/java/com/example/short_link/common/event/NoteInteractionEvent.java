package com.example.short_link.common.event;

// Published by the note slice after the change commits. The actor is a member or, for likes and
// boosts from other servers, a remote account; exactly one of the two is set.
public record NoteInteractionEvent(
    Type type,
    Long recipientUserId,
    Long actorUserId,
    Long actorRemoteId,
    Long noteId,
    String noteExcerpt,
    Long sourceNoteId,
    String sourceExcerpt) {

  public enum Type {
    LIKE,
    REPOST,
    REPLY,
    QUOTE
  }

  public boolean isSelfAction() {
    return actorUserId != null && actorUserId.equals(recipientUserId);
  }
}
