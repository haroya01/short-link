package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteTrendsController {

  private final RemoteTrendingFeed trending;

  @GetMapping("/trending")
  public RemoteTrendingFeed.Page trending(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return trending.page(viewerId, cursor, limit);
  }
}
