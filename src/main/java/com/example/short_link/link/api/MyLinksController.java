package com.example.short_link.link.api;

import com.example.short_link.link.application.MyLinksQuery;
import com.example.short_link.link.application.MyLinksResult;
import com.example.short_link.link.application.MyLinksService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
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

  private final MyLinksService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @GetMapping("/me")
  public MyLinksPage myLinks(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String tag) {
    MyLinksQuery query = MyLinksQuery.of(page, size, q, tag);
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
                        my.tags()))
            .toList();
    return new MyLinksPage(items, result.total());
  }
}
