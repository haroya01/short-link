package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkEventsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkEventsController {

  private final LinkEventsService service;

  @GetMapping("/{shortCode}/events")
  public LinkEventsPage events(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.events(userId, shortCode, cursor, limit);
  }
}
