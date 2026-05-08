package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkVisibilityService;
import jakarta.validation.constraints.NotNull;
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
  public VisibilityResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody VisibilityRequest request) {
    boolean nowPublic = service.setStatsPublic(userId, shortCode, request.statsPublic());
    return new VisibilityResponse(shortCode, nowPublic);
  }

  public record VisibilityRequest(@NotNull Boolean statsPublic) {}

  public record VisibilityResponse(String shortCode, boolean statsPublic) {}
}
