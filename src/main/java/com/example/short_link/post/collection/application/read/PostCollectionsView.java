package com.example.short_link.post.collection.application.read;

import java.util.List;

/**
 * 한 글이 속한 *공개* 컬렉션들 — 배치 조회 한 줄({@code postId} → 그 글이 담긴 길들). 피드가 보이는 카드 id 를 모아 한 번에 물어 "소속 한 올"을
 * N+1 없이 켠다. 어느 공개 컬렉션에도 없으면 {@code collections} 는 빈 목록.
 */
public record PostCollectionsView(Long postId, List<CollectionSummaryView> collections) {}
