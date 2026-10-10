package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.application.read.QuotingPostsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/notes")
@RequiredArgsConstructor
public class PublicQuotingPostsController {

  private final QuotingPostsQueryService quotingPosts;

  @GetMapping("/{noteId}/posts")
  public PublicFeedView posts(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long noteId,
      @RequestParam(defaultValue = "0") int page) {
    return quotingPosts.ofNote(noteId, viewerId, page);
  }
}
