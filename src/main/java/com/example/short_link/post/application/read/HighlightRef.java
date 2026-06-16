package com.example.short_link.post.application.read;

import java.time.Instant;

/** The just-created highlight echoed back to its author (no attribution needed — it's theirs). */
public record HighlightRef(
    Long id,
    Integer blockOrder,
    Integer endBlockOrder,
    Integer startOffset,
    Integer endOffset,
    String quote,
    Instant createdAt,
    String note) {}
