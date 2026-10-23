package com.example.short_link.home.presentation;

import com.example.short_link.federation.domain.SeenViewer;
import com.example.short_link.federation.presentation.RemoteTrendsController;
import com.example.short_link.home.application.read.HomeFeedQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HomeFeedController {

  private final HomeFeedQueryService home;

  @GetMapping("/api/v1/public/home")
  public HomeFeedQueryService.Page home(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = RemoteTrendsController.DEVICE_HEADER, required = false) String device,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String languages,
      @RequestParam(defaultValue = "latest") String tab,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String seen) {
    return home.page(
        viewerId,
        SeenViewer.of(viewerId, device),
        languages,
        tab,
        cursor,
        since,
        limit,
        RemoteTrendsController.HIDE_SEEN.equals(seen));
  }
}
