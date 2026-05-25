package com.example.short_link.link.presentation;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.application.read.MyLinksQueryService;
import com.example.short_link.link.presentation.response.MyLinkResponse;
import com.example.short_link.link.presentation.response.MyLinksPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class MyLinksController {

  private final MyLinksQueryService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @GetMapping("/me")
  public MyLinksPage myLinks(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) String expiry,
      @RequestParam(required = false) String createdAfter,
      @RequestParam(required = false) String createdBefore) {
    MyLinksQuery query =
        MyLinksQuery.of(size, after, q, tag, domain, expiry, createdAfter, createdBefore);
    MyLinksResult result = service.myLinks(userId, query);
    List<MyLinkResponse> items =
        result.items().stream()
            .map(
                my ->
                    new MyLinkResponse(
                        my.shortCode(),
                        urlBuilder.build(my.shortCode()),
                        my.originalUrl(),
                        my.createdAt(),
                        my.expiresAt(),
                        my.clickCount(),
                        my.tags(),
                        my.clicksLast7d()))
            .toList();
    return new MyLinksPage(items, result.nextCursor(), result.hasMore());
  }
}
