package com.example.short_link.common.event;

// Published by the note slice after the change commits. The actor is a member or, for likes and
// boosts from other servers, a remote account; exactly one of the two is set. conversationId is the
// thread the note belongs to, so a recipient who muted that conversation hears nothing of it.
public record NoteInteractionEvent(
    Type type,
    Long recipientUserId,
    Long actorUserId,
    Long actorRemoteId,
    Long noteId,
    String noteExcerpt,
    Long sourceNoteId,
    String sourceExcerpt,
    Long conversationId) {

  public NoteInteractionEvent(
      Type type,
      Long recipientUserId,
      Long actorUserId,
      Long actorRemoteId,
      Long noteId,
      String noteExcerpt,
      Long sourceNoteId,
      String sourceExcerpt) {
    this(
        type,
        recipientUserId,
        actorUserId,
        actorRemoteId,
        noteId,
        noteExcerpt,
        sourceNoteId,
        sourceExcerpt,
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
    return actorUserId != null && actorUserId.equals(recipientUserId);
  }
}
