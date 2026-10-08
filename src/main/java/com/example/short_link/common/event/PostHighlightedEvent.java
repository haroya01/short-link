package com.example.short_link.common.event;

public record PostHighlightedEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    Long highlightId) {

  public boolean isSelfAction() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }
}
