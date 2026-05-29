package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "피드" tab — published posts from the authors the logged-in user follows. Authenticated (not
 * under /api/v1/public), so {@code anyRequest().authenticated()} guards it.
 */
@RestController
@RequestMapping("/api/v1/feed/following")
@RequiredArgsConstructor
public class FollowingFeedController {

  private static final int MAX_SIZE = 50;

  private final PublicFeedQueryService publicFeedQueryService;

  @GetMapping
  public PublicFeedView feed(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
    return publicFeedQueryService.feedFollowing(userId, safePage, safeSize);
  }
}
