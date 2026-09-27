package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.SuggestedAuthorView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/authors")
@RequiredArgsConstructor
public class PublicAuthorController {

  private static final int MAX_LIMIT = 20;

  private final PublicFeedQueryService publicFeedQueryService;

  @GetMapping
  public List<SuggestedAuthorView> suggested(@RequestParam(defaultValue = "5") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    return publicFeedQueryService.suggestedAuthors(safeLimit);
  }
}
