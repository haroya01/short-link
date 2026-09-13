package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.DiscoverFeedQueryService;
import com.example.short_link.post.collection.application.read.DiscoverFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code scope=global}은 폴백 후 페이지네이션을 전역 피드로 고정한다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiscoverFeedController {

  private final DiscoverFeedQueryService discoverFeedQuery;

  @GetMapping("/feed/connections")
  public DiscoverFeedView connections(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String scope) {
    return discoverFeedQuery.feed(userId, page, size, "global".equals(scope));
  }
}
