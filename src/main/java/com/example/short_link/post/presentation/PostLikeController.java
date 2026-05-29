package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostLikeQueryService;
import com.example.short_link.post.application.read.PostLikeStatus;
import com.example.short_link.post.application.write.LikePostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated like (공감) on a post. Public like counts come from the public post views. */
@RestController
@RequestMapping("/api/v1/posts/{postId}/like")
@RequiredArgsConstructor
public class PostLikeController {

  private final LikePostUseCase likePost;
  private final PostLikeQueryService postLikeQueryService;

  @GetMapping
  public PostLikeStatus status(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return postLikeQueryService.status(userId, postId);
  }

  @PutMapping
  public PostLikeStatus like(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return likePost.like(userId, postId);
  }

  @DeleteMapping
  public PostLikeStatus unlike(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return likePost.unlike(userId, postId);
  }
}
