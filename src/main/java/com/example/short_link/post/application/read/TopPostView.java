package com.example.short_link.post.application.read;

/**
 * A row in the author overview's per-post table — lifetime traction per post, including the follows
 * the post drove ("이 글로 늘어난 팔로우").
 */
public record TopPostView(
    Long postId, String slug, String title, long viewCount, long likeCount, long followsGained) {}
