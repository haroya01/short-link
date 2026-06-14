package com.example.short_link.post.application.read;

import java.time.Instant;

/** A reader's own highlight in their library — carries the post reference to link back. */
public record MyHighlightView(
    Long id,
    String quote,
    Integer blockOrder,
    String postUsername,
    String postSlug,
    String postTitle,
    Instant createdAt) {}
