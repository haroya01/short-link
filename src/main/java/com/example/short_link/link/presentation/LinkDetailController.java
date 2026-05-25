package com.example.short_link.link.presentation;

import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.application.read.LinkDetailQueryService;
import com.example.short_link.link.presentation.response.LinkDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkDetailController {

  private final LinkDetailQueryService service;

  @GetMapping("/{shortCode}/detail")
  public LinkDetailResponse detail(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    LinkDetailView v = service.detail(userId, shortCode);
    return new LinkDetailResponse(
        v.shortCode(),
        v.originalUrl(),
        v.expiresAt(),
        v.ogTitle(),
        v.ogDescription(),
        v.ogImage(),
        v.ogTitleOverride(),
        v.ogDescriptionOverride(),
        v.ogImageOverride(),
        v.passwordProtected(),
        v.maxViews(),
        v.viewCount(),
        v.statsPublic(),
        v.tags(),
        v.note(),
        v.expiredMessage());
  }
}
