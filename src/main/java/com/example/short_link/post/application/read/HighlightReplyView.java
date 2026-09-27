package com.example.short_link.post.application.read;

import java.time.Instant;

public record HighlightReplyView(
    Long id, PublicAuthorView author, String body, Instant createdAt) {}
