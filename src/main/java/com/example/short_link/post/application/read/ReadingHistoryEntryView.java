package com.example.short_link.post.application.read;

import java.time.Instant;

public record ReadingHistoryEntryView(
    Long postId,
    Long userId,
    String username,
    String avatarUrl,
    String title,
    String slug,
    String excerpt,
    String ogImageUrl,
    String thumbnailUrl,
    Instant readAt) {}
