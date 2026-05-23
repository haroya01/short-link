package com.example.short_link.link.api;

import com.example.short_link.link.application.OgOverrideResult;
import com.example.short_link.link.application.OgOverrideService;
import jakarta.validation.Valid;
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
public class OgOverrideController {

  private final OgOverrideService service;

  @PatchMapping("/{shortCode}/og")
  public OgOverrideResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @Valid @RequestBody OgOverrideRequest request) {
    OgOverrideResult r =
        service.update(
            userId, shortCode, request.ogTitle(), request.ogDescription(), request.ogImage());
    return new OgOverrideResponse(r.shortCode(), r.ogTitle(), r.ogDescription(), r.ogImage());
  }

  public record OgOverrideResponse(
      String shortCode, String ogTitle, String ogDescription, String ogImage) {}
}
