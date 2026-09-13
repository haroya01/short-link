package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Emitted only on the first publish, excluding edits, re-publishing, and scheduled parking. Fans
 * out notifications to the author's followers, excluding the author. Slug and title are snapshots;
 * the author's handle is resolved when notifications are read.
 */
public record PostPublishedEvent(
    Long authorUserId, Long postId, String postSlug, String postTitle, Instant occurredAt) {}
