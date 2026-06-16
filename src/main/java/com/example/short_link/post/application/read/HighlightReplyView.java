package com.example.short_link.post.application.read;

import java.time.Instant;

/** A public, attributed reply in a highlight's flat thread — who replied with what. */
public record HighlightReplyView(
    Long id, PublicAuthorView author, String body, Instant createdAt) {}
