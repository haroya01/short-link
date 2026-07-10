package com.example.short_link.post.application.read;

import java.util.List;

/** GET /api/v1/highlights/feed 응답 — 팔로우한 큐레이터가 최근 칠한 구절 한 페이지(최신순). */
public record HighlightFeedView(
    List<HighlightFeedItem> items, int page, int size, boolean hasNext) {}
