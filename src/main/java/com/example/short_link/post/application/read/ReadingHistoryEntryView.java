package com.example.short_link.post.application.read;

import java.time.Instant;

/** One entry in the reader's history — the post + its author + when it was (last) read. */
public record ReadingHistoryEntryView(
    Long postId,
    String username,
    String avatarUrl,
    String title,
    String slug,
    String excerpt,
    String ogImageUrl,
    Instant readAt) {}
