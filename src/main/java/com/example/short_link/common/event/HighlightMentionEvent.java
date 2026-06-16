package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published once per user @-mentioned in a highlight reply. Notification-only (webhooks don't
 * subscribe to mentions), so it's a separate event from {@link BlogInteractionEvent}. The mentioned
 * user may not own the post, so {@code postAuthorUsername} is carried to build the post link — same
 * shape as {@link CommentMentionEvent}.
 *
 * @param recipientUserId the mentioned user
 * @param actorUserId who wrote the reply (used to skip self-mentions and resolve a name)
 * @param postId the post the highlight lives on
 * @param postSlug post slug snapshot
 * @param postTitle post title snapshot
 * @param postAuthorUsername the post owner's handle, for building the post link
 * @param occurredAt when the reply was written
 */
public record HighlightMentionEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Instant occurredAt) {}
