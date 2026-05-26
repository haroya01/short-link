package com.example.short_link.link.stats.presentation;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkStatsController {

  private final LinkStatsQueryService service;

  @GetMapping("/{shortCode}/stats")
  public LinkStats stats(@AuthenticationPrincipal Long userId, @PathVariable ShortCode shortCode) {
    return service.stats(userId, shortCode);
  }
}
