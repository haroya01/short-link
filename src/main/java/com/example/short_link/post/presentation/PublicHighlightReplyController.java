package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PostHighlightReplyQueryService;
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
@RequestMapping("/api/v1/public/highlights")
@RequiredArgsConstructor
public class PublicHighlightReplyController {

  private final PostHighlightReplyQueryService replyQuery;

  @GetMapping("/{highlightId}/replies")
  public ResponseEntity<List<HighlightReplyView>> list(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable Long highlightId,
      HttpServletRequest req) {
    return PostResponses.of(
        replyQuery.listForHighlight(highlightId, PostViewerHeaders.from(viewerId, req)));
  }
}
