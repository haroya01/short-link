package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published after a reply commits, for notifications only. The recipient is the parent comment's
 * author. {@code postAuthorUsername} builds the post link because the recipient may not own the
 * post. Slug, title, and author username are snapshots; the consumer skips self-replies.
 */
public record CommentReplyEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Instant occurredAt) {

  public boolean isSelfReply() {
    return recipientUserId != null && recipientUserId.equals(actorUserId);
  }
}
