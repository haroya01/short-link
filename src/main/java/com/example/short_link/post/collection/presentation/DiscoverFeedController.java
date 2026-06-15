package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.application.read.DiscoverFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발견 피드 — 큐레이터 연결 흐름(§0 "발견 = 큐레이터의 연결을 따라가기"). 내가 팔로우한 사람들의 공개 컬렉션에 최근 이어진 연결을 최신순으로. 인증 면(내 팔로우
 * 그래프 기준).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiscoverFeedController {

  private final DiscoverFeedQueryService discoverFeedQuery;

  @GetMapping("/feed/connections")
  public DiscoverFeedView connections(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return discoverFeedQuery.feed(userId, page, size);
  }
}
