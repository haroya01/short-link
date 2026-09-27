package com.example.short_link.post.application.read;

import java.time.Instant;

public record HighlightView(
    Long id,
    PublicAuthorView author,
    Integer blockOrder,
    Integer endBlockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    Instant createdAt,
    String note,
    long replyCount) {}
