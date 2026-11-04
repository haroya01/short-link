package com.example.short_link.post.presentation;

import com.example.short_link.common.page.Keyset;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.CommentsView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicCommentController {

  private static final int DEFAULT_PAGE = 20;
  private static final int MAX_PAGE = 50;

  private final PostCommentQueryService postCommentQueryService;

  // Clients that can draw a deleted comment as an empty place ask for it with tombstones=1;
  // builds that cannot decode one get the list without such places.
  @GetMapping("/{postId}/comments")
  public ResponseEntity<List<CommentView>> list(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long postId,
      @RequestParam(name = "tombstones", defaultValue = "false") boolean tombstones,
      HttpServletRequest req) {
    return PostResponses.of(
        postCommentQueryService.listForPost(
            postId, PostViewerHeaders.from(viewerId, req), tombstones));
  }

  @GetMapping("/{postId}/comments/page")
  public ResponseEntity<CommentsView> page(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long postId,
      @RequestParam(defaultValue = "0") long cursor,
      @RequestParam(required = false) Long after,
      @RequestParam(required = false) Integer limit,
      @RequestParam(name = "tombstones", defaultValue = "false") boolean tombstones,
      HttpServletRequest req) {
    PostViewer reader = PostViewerHeaders.from(viewerId, req);
    int size = Keyset.limit(limit, DEFAULT_PAGE, MAX_PAGE);
    return PostResponses.of(
        after == null
            ? postCommentQueryService.page(postId, reader, tombstones, cursor, size)
            : postCommentQueryService.newer(postId, reader, tombstones, after, size));
  }
}
