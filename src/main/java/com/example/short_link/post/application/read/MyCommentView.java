package com.example.short_link.post.application.read;

import java.time.Instant;

public record MyCommentView(
    Long id,
    String body,
    Long parentId,
    long likeCount,
    Instant createdAt,
    String postSlug,
    String postTitle,
    String postUsername) {}
