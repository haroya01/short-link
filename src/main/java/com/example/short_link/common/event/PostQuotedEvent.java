package com.example.short_link.common.event;

public record PostQuotedEvent(
    Long recipientUserId, Long actorUserId, Long noteId, String noteExcerpt) {

  public boolean isSelfAction() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }
}
