package com.example.short_link.post.collection.application.read;

import java.time.Instant;
import java.util.List;

/**
 * 컬렉션 목록 한 줄 — 제목·소개·공개범위·담긴 수 + 최근 항목 미리보기(어디 넣을지 떠올리게). 좋아요·팔로워 수 없음(§0 바깥은 조용히). {@code preview}
 * = 최근 담긴 항목 라벨 몇 개.
 */
public record CollectionSummaryView(
    Long id,
    String title,
    String description,
    String visibility,
    int count,
    Instant updatedAt,
    List<String> preview) {}
