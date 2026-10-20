package com.example.short_link.home.presentation;

import com.example.short_link.home.application.read.HomeFeedQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeFeedController {

  private final HomeFeedQueryService home;

  @GetMapping("/api/v1/public/home")
  public HomeFeedQueryService.Page home(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "latest") String tab,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return home.page(viewerId, tab, cursor, limit);
  }
}
