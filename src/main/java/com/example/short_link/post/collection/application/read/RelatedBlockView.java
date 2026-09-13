package com.example.short_link.post.collection.application.read;

/** {@code sharedCount}는 이 블록과 대상 블록을 함께 담은 공개 컬렉션 수다. 여러 컬렉션을 합산하므로 개별 연결의 id와 why는 포함하지 않는다. */
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
