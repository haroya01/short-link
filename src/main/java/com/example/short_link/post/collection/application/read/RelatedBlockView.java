package com.example.short_link.post.collection.application.read;

public record RelatedBlockView(
    String blockType,
    Long refId,
    String title,
    String excerpt,
    String slug,
    String username,
    String quote,
    String body,
    int sharedCount) {}
