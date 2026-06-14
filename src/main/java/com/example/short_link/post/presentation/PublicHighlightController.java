package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightView;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, attributed highlights on a post — rendered inline as social highlights for any reader.
 */
@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicHighlightController {

  private final PostHighlightQueryService highlightQuery;

  @GetMapping("/{postId}/highlights")
  public List<HighlightView> list(@PathVariable Long postId) {
    return highlightQuery.listForPost(postId);
  }
}
