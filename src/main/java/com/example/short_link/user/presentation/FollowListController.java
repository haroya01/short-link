package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.FollowListQueryService;
import com.example.short_link.user.application.read.FollowListView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous viewers have followedByMe=false. When counts are hidden, only the author can read the
 * lists; others receive 403.
 */
@RestController
@RequestMapping("/api/v1/users/{username}")
@RequiredArgsConstructor
public class FollowListController {

  private static final int MAX_SIZE = 50;

  private final FollowListQueryService followListQueryService;

  @GetMapping("/followers")
  public FollowListView followers(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return followListQueryService.followers(viewerId, username, safePage(page), safeSize(size));
  }

  @GetMapping("/following")
  public FollowListView following(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return followListQueryService.following(viewerId, username, safePage(page), safeSize(size));
  }

  private int safePage(int page) {
    return Math.max(page, 0);
  }

  private int safeSize(int size) {
    return Math.min(Math.max(size, 1), MAX_SIZE);
  }
}
