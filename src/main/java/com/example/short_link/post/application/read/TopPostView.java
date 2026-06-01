package com.example.short_link.post.application.read;

/** A row in the author overview's "top posts" table — lifetime traction per post. */
public record TopPostView(Long postId, String slug, String title, long viewCount, long likeCount) {}
