package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.application.read.DiscoverFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 발견 피드 — 전역의 *공개* 컬렉션에 최근 이어진 연결을 최신순으로(비로그인 첫 표면). 개인화판(GET /feed/connections)과 같은 조립을 쓰되 팔로우
 * 게이트만 벗긴다: 알고리즘 랭킹이 아니라 사람의 큐레이션을 그대로 흘린다. GET /api/v1/public/** 은 이미 permitAll — 미로그인 독자도 본다.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConnectionFeedController {

  private static final int MAX_PAGE_SIZE = 50;

  private final DiscoverFeedQueryService discoverFeedQuery;

  @GetMapping("/feed/connections")
  public DiscoverFeedView connections(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    // 익명 표면 — size 를 상한으로 묶어 ?size=2000000 같은 요청이 대형 쿼리·팬아웃으로 번지지 않게 한다(형제 피드와 같은 상한).
    return discoverFeedQuery.publicFeed(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
  }
}
