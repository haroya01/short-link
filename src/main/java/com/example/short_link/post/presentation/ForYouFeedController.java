package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.ForYouQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "For You" tab — a personalized discovery feed ranked by the reader's tag affinity.
 * Authenticated (not under /api/v1/public), so {@code anyRequest().authenticated()} guards it.
 */
@RestController
@RequestMapping("/api/v1/feed/for-you")
@RequiredArgsConstructor
public class ForYouFeedController {

  private static final int MAX_SIZE = 50;

  private final ForYouQueryService forYouQueryService;

  @GetMapping
  public PublicFeedView feed(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return forYouQueryService.feedForYou(
        userId, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
  }
}
