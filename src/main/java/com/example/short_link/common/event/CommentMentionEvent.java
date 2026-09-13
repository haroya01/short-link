package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published once per mentioned user for notifications only. {@code postAuthorUsername} builds the
 * post link because the recipient may not own the post. Slug, title, and author username are
 * snapshots; the consumer skips self-mentions.
 */
public record CommentMentionEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Instant occurredAt) {}
