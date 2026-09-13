package com.example.short_link.post.application.read;

import java.time.Instant;

/** Replies are flat; {@code parentId} identifies the top-level comment for client-side nesting. */
public record CommentView(
    Long id,
    Long parentId,
    PublicAuthorView author,
    String body,
    Instant createdAt,
    long likeCount) {}
