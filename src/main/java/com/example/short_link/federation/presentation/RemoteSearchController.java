package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.RemoteAccountSearch;
import com.example.short_link.federation.application.RemoteResolution;
import com.example.short_link.federation.presentation.response.RemoteAccountListResponse;
import com.example.short_link.federation.presentation.response.RemoteAccountSearchResponse;
import com.example.short_link.federation.presentation.response.RemoteResolveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteSearchController {

  private final RemoteAccountSearch search;
  private final RemoteResolution resolution;

  @GetMapping("/accounts/search")
  public RemoteAccountSearchResponse search(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "10") int limit) {
    return new RemoteAccountSearchResponse(search.search(viewerId, q, limit));
  }

  @GetMapping("/accounts/suggestions")
  public RemoteAccountListResponse suggestions(
      @AuthenticationPrincipal Long viewerId, @RequestParam(defaultValue = "5") int limit) {
    return new RemoteAccountListResponse(search.suggestions(viewerId, limit));
  }

  @GetMapping("/resolve")
  public RemoteResolveResponse resolve(
      @AuthenticationPrincipal Long viewerId, @RequestParam(defaultValue = "") String q) {
    return RemoteResolveResponse.of(resolution.resolve(viewerId, q));
  }
}
