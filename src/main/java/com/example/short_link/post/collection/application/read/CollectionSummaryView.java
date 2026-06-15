package com.example.short_link.post.collection.application.read;

import java.time.Instant;

/** 컬렉션 목록 한 줄 — 제목·소개·공개범위·담긴 수만. 좋아요·팔로워 수 없음(§0 바깥은 조용히). */
public record CollectionSummaryView(
    Long id, String title, String description, String visibility, int count, Instant updatedAt) {}
