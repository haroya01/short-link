package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicSeriesDetail;
import com.example.short_link.post.application.read.PublicSeriesListView;
import com.example.short_link.post.application.read.PublicSeriesQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles/{username}/series")
@RequiredArgsConstructor
public class PublicSeriesController {

  private final PublicSeriesQueryService publicSeriesQueryService;

  @GetMapping
  public PublicSeriesListView list(@PathVariable String username) {
    return publicSeriesQueryService.listPublicSeries(username);
  }

  @GetMapping("/{slug}")
  public PublicSeriesDetail get(@PathVariable String username, @PathVariable String slug) {
    return publicSeriesQueryService.findPublicSeries(username, slug);
  }
}
