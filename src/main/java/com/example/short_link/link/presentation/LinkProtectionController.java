package com.example.short_link.link.presentation;

import com.example.short_link.link.application.LinkProtectionService;
import com.example.short_link.link.application.dto.LinkProtectionResult;
import com.example.short_link.link.presentation.request.LinkProtectionRequest;
import com.example.short_link.link.presentation.response.LinkProtectionResponse;
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
public class LinkProtectionController {

  private final LinkProtectionService service;

  @PatchMapping("/{shortCode}/protection")
  public LinkProtectionResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @Valid @RequestBody LinkProtectionRequest request) {
    return toResponse(service.update(userId, shortCode, request.password(), request.maxViews()));
  }

  private static LinkProtectionResponse toResponse(LinkProtectionResult result) {
    return new LinkProtectionResponse(
        result.shortCode(), result.passwordProtected(), result.maxViews(), result.viewCount());
  }
}
