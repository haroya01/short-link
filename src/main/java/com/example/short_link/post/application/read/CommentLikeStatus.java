package com.example.short_link.post.application.read;

/** Result of a comment like/unlike — the authoritative count plus the caller's new state. */
public record CommentLikeStatus(long likeCount, boolean liked) {}
