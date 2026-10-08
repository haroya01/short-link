package com.example.short_link.common.event;

public record CommentLikedEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    Long postAuthorId,
    Long commentId) {

  public boolean isSelfAction() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }
}
