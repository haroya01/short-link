package com.example.short_link.common.event;

// Published by the note slice after the change commits. The actor is a member or, for likes and
// boosts from other servers, a remote account. conversationId is the thread a recipient may have
// muted and roomId the room it lives in, which never leaves kurl, so no other server sends one.
public record NoteInteractionEvent(
    Type type,
    Long recipientUserId,
    Actor actor,
    Long noteId,
    String noteExcerpt,
    Long sourceNoteId,
    String sourceExcerpt,
    Long conversationId,
    Long roomId) {

  public NoteInteractionEvent(
      Type type,
      Long recipientUserId,
      Actor actor,
      Long noteId,
      String noteExcerpt,
      Long sourceNoteId,
      String sourceExcerpt,
      Long conversationId) {
    this(
        type,
        recipientUserId,
        actor,
        noteId,
        noteExcerpt,
        sourceNoteId,
        sourceExcerpt,
        conversationId,
        null);
  }

  public enum Type {
    LIKE,
    REPOST,
    REPLY,
    QUOTE,
    MENTION
  }

  public boolean isSelfAction() {
    return actor.userId() != null && actor.userId().equals(recipientUserId);
  }
}
