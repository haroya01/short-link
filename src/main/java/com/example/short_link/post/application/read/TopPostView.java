package com.example.short_link.post.application.read;

public record TopPostView(
    Long postId, String slug, String title, long viewCount, long likeCount, long followsGained) {}
