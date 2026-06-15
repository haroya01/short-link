package com.example.short_link.post.collection.application.read;

import java.util.List;

/** GET /api/v1/feed/connections 응답 — 큐레이터 연결 흐름 한 페이지. */
public record DiscoverFeedView(
    List<DiscoverConnectionView> items, int page, int size, boolean hasNext) {}
