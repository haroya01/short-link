package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published after a reply to a top-level comment commits. Distinct from the post-owner COMMENT
 * notice: the recipient here is the <em>parent comment's author</em>, who may not own the post — so
 * {@code postAuthorUsername} is carried to build the post link. Notification-only (webhooks don't
 * subscribe to replies), which is why it's a separate event from {@link BlogInteractionEvent}.
 *
 * @param recipientUserId the parent comment's author, who gets the reply notice
 * @param actorUserId who wrote the reply (used to skip self-replies and resolve a name)
 * @param postId the post the comment thread lives on
 * @param postSlug post slug snapshot
 * @param postTitle post title snapshot
 * @param postAuthorUsername the post owner's handle, for building the post link
 * @param occurredAt when the reply happened
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
