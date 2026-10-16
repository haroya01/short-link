package com.example.short_link.common.event;

import java.time.Instant;

// Emitted once per post and threshold: readers is 1 for the first person to read the post, then
// 10, 100 and 1000. Slug and title are snapshots.
public record PostReadMilestoneEvent(
    Long authorUserId,
    Long postId,
    String postSlug,
    String postTitle,
    int readers,
    Instant occurredAt) {}
