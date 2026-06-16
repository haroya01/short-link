package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;
import java.time.Instant;

/**
 * 발견 피드 한 장 — "누가(curator) 무엇을(block) 어느 컬렉션에 이었나 + 왜". 블록은 종류별 평면 필드
 * 재사용(POST=title/excerpt/slug/username, HIGHLIGHT=quote+원문, NOTE=body). 공개 컬렉션만 흐른다.
 */
public record DiscoverConnectionView(
    Long id,
    PublicAuthorView curator,
    Long collectionId,
    String collectionTitle,
    String collectionKind,
    String why,
    Instant connectedAt,
    String blockType,
    String title,
    String excerpt,
    String slug,
    String username,
    String quote,
    String body) {}
