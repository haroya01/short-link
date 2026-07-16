package com.example.short_link.post.application.read;

import java.util.List;

/**
 * GET /api/v1/highlights/feed 응답 — 최근 칠해진 구절 한 페이지(최신순). {@code source} 는 이 페이지가 어느 흐름에서 왔는지: 팔로우한
 * 큐레이터들("following") 또는 콜드스타트 폴백·scope=global 고정의 전역 공개 하이라이트("global").
 */
public record HighlightFeedView(
    List<HighlightFeedItem> items, int page, int size, boolean hasNext, String source) {

  public static final String SOURCE_FOLLOWING = "following";
  public static final String SOURCE_GLOBAL = "global";
}
