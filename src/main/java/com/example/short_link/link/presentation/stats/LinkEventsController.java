package com.example.short_link.link.presentation.stats;

import com.example.short_link.link.application.LinkEventsService;
import com.example.short_link.link.application.dto.LinkEventsResult;
import com.example.short_link.link.presentation.response.LinkEventResponse;
import com.example.short_link.link.presentation.response.LinkEventsPage;
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
    return toResponse(service.events(userId, shortCode, cursor, limit));
  }

  private static LinkEventsPage toResponse(LinkEventsResult result) {
    return new LinkEventsPage(
        result.items().stream()
            .map(
                item ->
                    new LinkEventResponse(
                        item.clickedAt(),
                        item.country(),
                        item.region(),
                        item.city(),
                        item.device(),
                        item.os(),
                        item.browser(),
                        item.referrer(),
                        item.referrerHost(),
                        item.channel(),
                        item.language(),
                        item.bot(),
                        item.botName(),
                        item.ipMasked()))
            .toList(),
        result.nextCursor());
  }
}
