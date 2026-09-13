package com.example.short_link.post.application.read;

/** Counters are lifetime totals; {@code followsGained} counts follows attributed to this post. */
public record TopPostView(
    Long postId, String slug, String title, long viewCount, long likeCount, long followsGained) {}
