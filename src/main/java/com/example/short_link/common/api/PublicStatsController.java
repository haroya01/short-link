package com.example.short_link.common.api;

import com.example.short_link.common.application.PublicStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicStatsController {

  private final PublicStatsService service;

  @GetMapping("/stats")
  public PublicStats stats() {
    return service.totals();
  }

  public record PublicStats(long links, long clicks) {}
}
