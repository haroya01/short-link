package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published after a reply to a reader highlight commits. The recipient is the <em>highlight's
 * author</em> (whoever opened the thread with the highlight / note), who may not own the post — so
 * {@code postAuthorUsername} is carried to build the post link, exactly like {@link
 * CommentReplyEvent}. Notification-only (webhooks don't subscribe to highlight replies), which is
 * why it's a separate event from {@link BlogInteractionEvent}.
 *
 * @param recipientUserId the highlight's author, who gets the reply notice
 * @param actorUserId who wrote the reply (used to skip self-replies and resolve a name)
 * @param postId the post the highlight lives on
 * @param postSlug post slug snapshot
 * @param postTitle post title snapshot
 * @param postAuthorUsername the post owner's handle, for building the post link
 * @param occurredAt when the reply happened
 */
public record HighlightReplyEvent(
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
