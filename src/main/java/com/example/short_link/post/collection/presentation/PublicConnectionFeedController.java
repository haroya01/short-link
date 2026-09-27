package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.application.read.DiscoverFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConnectionFeedController {

  private static final int MAX_PAGE_SIZE = 50;

  private final DiscoverFeedQueryService discoverFeedQuery;

  @GetMapping("/feed/connections")
  public DiscoverFeedView connections(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    // 익명 요청의 대형 쿼리와 팬아웃을 막도록 페이지 크기를 제한한다.
    return discoverFeedQuery.publicFeed(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
  }
}
