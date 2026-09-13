package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published once per user mentioned in a highlight reply, for notifications only. {@code
 * postAuthorUsername} builds the post link because the recipient may not own the post. Slug, title,
 * and author username are snapshots; the consumer skips self-mentions.
 */
public record HighlightMentionEvent(
    Long recipientUserId,
    Long actorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Instant occurredAt) {}
