package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.FollowQueryService;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.application.write.FollowUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Follow / unfollow an author. GET is public (follower count + a {@code following=false} for
 * anonymous viewers); PUT/DELETE require auth.
 */
@RestController
@RequestMapping("/api/v1/users/{username}/follow")
@RequiredArgsConstructor
public class FollowController {

  private final FollowUseCase followUseCase;
  private final FollowQueryService followQueryService;

  @GetMapping
  public FollowStatus status(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    return followQueryService.status(userId, username);
  }

  @PutMapping
  public FollowStatus follow(@AuthenticationPrincipal Long userId, @PathVariable String username) {
    return followUseCase.follow(userId, username);
  }

  @DeleteMapping
  public FollowStatus unfollow(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    return followUseCase.unfollow(userId, username);
  }
}
