package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostLikeQueryService;
import com.example.short_link.post.application.read.PublicFeedItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated list of the caller's liked posts (the "내가 좋아요한 글" surface), as feed cards. */
@RestController
@RequestMapping("/api/v1/users/me/likes")
@RequiredArgsConstructor
public class MyLikesController {

  private final PostLikeQueryService postLikeQueryService;

  @GetMapping
  public List<PublicFeedItem> myLikes(@AuthenticationPrincipal Long userId) {
    return postLikeQueryService.likedPosts(userId);
  }
}
