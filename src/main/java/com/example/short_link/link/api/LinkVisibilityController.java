package com.example.short_link.link.api;

import com.example.short_link.link.api.request.LinkVisibilityRequest;
import com.example.short_link.link.api.response.LinkVisibilityResponse;
import com.example.short_link.link.application.LinkVisibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkVisibilityController {

  private final LinkVisibilityService service;

  @PatchMapping("/{shortCode}/visibility")
  public LinkVisibilityResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody LinkVisibilityRequest request) {
    boolean nowPublic = service.setStatsPublic(userId, shortCode, request.statsPublic());
    return new LinkVisibilityResponse(shortCode, nowPublic);
  }
}
