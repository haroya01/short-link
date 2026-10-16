package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightView;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicHighlightController {

  private final PostHighlightQueryService highlightQuery;

  @GetMapping("/{postId}/highlights")
  public ResponseEntity<List<HighlightView>> list(
      @AuthenticationPrincipal Long viewerId, @PathVariable Long postId, HttpServletRequest req) {
    return PostResponses.of(
        highlightQuery.listForPost(postId, PostReaderHeaders.from(viewerId, req)));
  }
}
