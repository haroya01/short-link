package com.example.short_link.post.collection.application.read;

import java.util.List;

/** 컬렉션 상세 — 헤더(제목·소개·공개범위·큐레이터) + 연결된 블록들(큐레이터 순서). */
public record CollectionDetailView(
    Long id,
    String title,
    String description,
    String visibility,
    String kind,
    String curatorUsername,
    List<ConnectionView> connections) {}
