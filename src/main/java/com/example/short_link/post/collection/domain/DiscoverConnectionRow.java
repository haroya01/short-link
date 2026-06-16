package com.example.short_link.post.collection.domain;

import java.time.Instant;

/**
 * 발견 피드 한 행(연결 × 컬렉션 조인 투영) — 팔로우한 큐레이터의 공개 컬렉션에 최근 이어진 것. 블록 본문은 아직 미해석(refId): 서비스가 일괄로
 * 글·하이라이트·노트로 푼다.
 */
public record DiscoverConnectionRow(
    Long connectionId,
    ConnectionBlockType blockType,
    Long refId,
    String why,
    Instant connectedAt,
    Long collectionId,
    String collectionTitle,
    CollectionKind kind,
    Long ownerId) {}
