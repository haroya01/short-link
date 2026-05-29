package com.example.short_link.post.application.read;

import java.util.List;

/** GET /api/v1/public/posts 응답 — 전역 공개 피드 한 페이지. */
public record PublicFeedView(List<PublicFeedItem> items, int page, int size, boolean hasNext) {}
