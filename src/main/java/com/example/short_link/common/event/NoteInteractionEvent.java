package com.example.short_link.common.event;

// Published by the note slice after the change commits. The actor is a member or, for likes and
// boosts from other servers, a remote account. conversationId is the thread the note belongs to,
// so a recipient who muted that conversation hears nothing of it.
public record NoteInteractionEvent(
    Type type,
    Long recipientUserId,
    Actor actor,
    Long noteId,
    String noteExcerpt,
    Long sourceNoteId,
    String sourceExcerpt,
    Long conversationId) {

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
