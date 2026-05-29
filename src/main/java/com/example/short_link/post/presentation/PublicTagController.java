package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.domain.TagCount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public 주제 (tag) exploration — the most-used tags across published posts. */
@RestController
@RequestMapping("/api/v1/public/tags")
@RequiredArgsConstructor
public class PublicTagController {

  private static final int MAX_LIMIT = 100;

  private final PublicFeedQueryService publicFeedQueryService;

  @GetMapping
  public List<TagCount> popular(@RequestParam(defaultValue = "50") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    return publicFeedQueryService.popularTags(safeLimit);
  }
}
