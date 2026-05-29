package com.example.short_link.post.application.read;

/** Like state for a post: total count + whether the current user has liked it. */
public record PostLikeStatus(long likeCount, boolean liked) {}
