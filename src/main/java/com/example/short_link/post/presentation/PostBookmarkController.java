package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.BookmarkView;
import com.example.short_link.post.application.read.PostBookmarkQueryService;
import com.example.short_link.post.application.read.PostBookmarkStatus;
import com.example.short_link.post.application.write.BookmarkPostUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated bookmarks (reading list). Toggle/status live under a post; the list of the caller's
 * bookmarks is its own collection resource.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostBookmarkController {

  private final BookmarkPostUseCase bookmarkPost;
  private final PostBookmarkQueryService postBookmarkQueryService;

  @GetMapping("/posts/{postId}/bookmark")
  public PostBookmarkStatus status(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return postBookmarkQueryService.status(userId, postId);
  }

  @PutMapping("/posts/{postId}/bookmark")
  public PostBookmarkStatus bookmark(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return bookmarkPost.bookmark(userId, postId);
  }

  @DeleteMapping("/posts/{postId}/bookmark")
  public PostBookmarkStatus unbookmark(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return bookmarkPost.unbookmark(userId, postId);
  }

  @GetMapping("/bookmarks")
  public List<BookmarkView> myBookmarks(@AuthenticationPrincipal Long userId) {
    return postBookmarkQueryService.list(userId);
  }
}
