package com.example.short_link.post.application.read;

import java.time.Instant;

/**
 * One entry in the "남들 하이라이트" feed — a highlight a followed curator drew, carried with the post it
 * lives in so the reader can jump to the passage (block anchors) or its thread. Combines {@link
 * HighlightView}'s attribution + anchors + reply count with {@link MyHighlightView}'s post
 * reference (slug/title/author). {@code curator} is who highlighted; {@code postAuthorUsername}
 * wrote the post.
 */
public record HighlightFeedItem(
    Long id,
    Long postId,
    PublicAuthorView curator,
    String postSlug,
    String postTitle,
    String postAuthorUsername,
    Integer blockOrder,
    Integer endBlockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    String note,
    Instant createdAt,
    long replyCount) {}
