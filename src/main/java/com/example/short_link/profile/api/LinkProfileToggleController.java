package com.example.short_link.profile.api;

import com.example.short_link.profile.application.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{shortCode}/profile")
@RequiredArgsConstructor
public class LinkProfileToggleController {

  private final ProfileService service;

  @PutMapping
  public ToggleResponse toggle(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody ToggleRequest request) {
    service.toggleLinkOnProfile(userId, shortCode, request.show());
    return new ToggleResponse(request.show());
  }

  public record ToggleRequest(boolean show) {}

  public record ToggleResponse(boolean show) {}
}
