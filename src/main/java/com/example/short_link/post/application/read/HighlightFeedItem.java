package com.example.short_link.post.application.read;

import java.time.Instant;

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
