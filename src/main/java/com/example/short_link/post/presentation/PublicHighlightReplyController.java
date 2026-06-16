package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PostHighlightReplyQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, attributed replies in a highlight's flat thread — rendered inline for any reader.
 * permitAll 는 GET /api/v1/public/** 가 커버.
 */
@RestController
@RequestMapping("/api/v1/public/highlights")
@RequiredArgsConstructor
public class PublicHighlightReplyController {

  private final PostHighlightReplyQueryService replyQuery;

  @GetMapping("/{highlightId}/replies")
  public List<HighlightReplyView> list(@PathVariable Long highlightId) {
    return replyQuery.listForHighlight(highlightId);
  }
}
