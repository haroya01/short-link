package com.example.short_link.common.event;

public record HighlightReplyLikedEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    Long postAuthorId,
    Long highlightId,
    Long replyId) {

  public boolean isSelfAction() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }
}
