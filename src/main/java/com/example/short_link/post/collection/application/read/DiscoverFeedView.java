package com.example.short_link.post.collection.application.read;

import java.util.List;

/**
 * GET /api/v1/feed/connections 응답 — 큐레이터 연결 흐름 한 페이지. {@code source} 는 이 페이지가 어느 흐름에서 왔는지: 팔로우 그래프
 * 개인화("following") 또는 콜드스타트 폴백·scope=global 고정의 전역 공개 피드("global").
 */
public record DiscoverFeedView(
    List<DiscoverConnectionView> items, int page, int size, boolean hasNext, String source) {

  public static final String SOURCE_FOLLOWING = "following";
  public static final String SOURCE_GLOBAL = "global";
}
