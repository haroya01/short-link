package com.example.short_link.link.api;

import com.example.short_link.link.application.MyLinksService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class MyLinksController {

  private final MyLinksService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @GetMapping("/me")
  public List<MyLinkResponse> myLinks(@AuthenticationPrincipal Long userId) {
    return service.myLinks(userId).stream()
        .map(
            my ->
                new MyLinkResponse(
                    my.shortCode(),
                    urlBuilder.build(my.shortCode()),
                    my.originalUrl(),
                    my.createdAt(),
                    my.expiresAt()))
        .toList();
  }
}
