package com.example.short_link.post.application.read;

import java.time.Instant;

public record CommentView(
    Long id,
    Long parentId,
    PublicAuthorView author,
    String body,
    Instant createdAt,
    long likeCount) {}
