package com.example.short_link.link.og.presentation;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.OgOverrideService;
import com.example.short_link.link.og.presentation.request.OgOverrideRequest;
import com.example.short_link.link.og.presentation.response.OgOverrideResponse;
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
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody OgOverrideRequest request) {
    return OgOverrideResponse.from(
        service.update(
            userId, shortCode, request.ogTitle(), request.ogDescription(), request.ogImage()));
  }
}
