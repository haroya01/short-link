package com.example.short_link.post.application.read;

import java.time.Instant;

/**
 * Public comment view. Flat (parentId carries the threading); the client nests replies under their
 * top-level parent. author is the hydrated commenter summary.
 */
public record CommentView(
    Long id, Long parentId, PublicAuthorView author, String body, Instant createdAt) {}
