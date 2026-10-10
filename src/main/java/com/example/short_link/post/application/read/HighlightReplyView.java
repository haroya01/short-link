package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

public record HighlightReplyView(
    Long id,
    PublicAuthorView author,
    String body,
    Instant createdAt,
    List<String> mentions,
    long likeCount,
    boolean liked) {}
