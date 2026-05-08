package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkStats;
import com.example.short_link.link.application.LinkStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class PublicLinkStatsController {

  private final LinkStatsService service;

  @GetMapping("/{shortCode}/public-stats")
  public LinkStats publicStats(@PathVariable String shortCode) {
    return service.publicStats(shortCode);
  }
}
