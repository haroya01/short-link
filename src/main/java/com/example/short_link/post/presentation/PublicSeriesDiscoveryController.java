package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicSeriesCard;
import com.example.short_link.post.application.read.PublicSeriesQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/series")
@RequiredArgsConstructor
public class PublicSeriesDiscoveryController {

  private static final int MAX_LIMIT = 20;

  private final PublicSeriesQueryService publicSeriesQueryService;

  @GetMapping
  public List<PublicSeriesCard> discover(@RequestParam(defaultValue = "6") int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    return publicSeriesQueryService.discoverSeries(safeLimit);
  }
}
