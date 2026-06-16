package com.example.short_link.post.application.read;

import java.time.Instant;

/** A public, attributed highlight on a post — who highlighted which span. */
public record HighlightView(
    Long id,
    PublicAuthorView author,
    Integer blockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    Instant createdAt,
    String note) {}
