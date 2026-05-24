package com.example.short_link.link.presentation;

import com.example.short_link.link.application.LinkVisibilityService;
import com.example.short_link.link.presentation.request.LinkVisibilityRequest;
import com.example.short_link.link.presentation.response.LinkVisibilityResponse;
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
