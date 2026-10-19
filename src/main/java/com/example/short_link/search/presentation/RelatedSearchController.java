package com.example.short_link.search.presentation;

import com.example.short_link.search.application.read.RelatedSearchQueryService;
import com.example.short_link.search.application.read.RelatedSearchView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RelatedSearchController {

  private final RelatedSearchQueryService related;

  @GetMapping("/api/v1/public/search/related")
  public RelatedSearchView related(
      @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "8") int limit) {
    return related.related(q, limit);
  }
}
