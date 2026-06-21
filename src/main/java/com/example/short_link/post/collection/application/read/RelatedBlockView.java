package com.example.short_link.post.collection.application.read;

/**
 * 이 블록과 같은 공개 컬렉션에 함께 놓인 다른 블록 — 발견의 한 홉(이것 → 이것과 이어진 것). {@code sharedCount} = 함께 놓인 공개 컬렉션 수(많을수록
 * 더 자주 같은 길에 묶였다는 사람의 신호). {@code why}·연결 id 는 의미가 없어(여러 컬렉션) 뺀다 — 블록 본문만.
 */
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
