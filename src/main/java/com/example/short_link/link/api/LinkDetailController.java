package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkDetailService;
import java.time.Instant;
import java.util.List;
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

  private final LinkDetailService service;

  @GetMapping("/{shortCode}/detail")
  public LinkDetailResponse detail(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    return service.detail(userId, shortCode);
  }

  public record LinkDetailResponse(
      String shortCode,
      String originalUrl,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage,
      String ogTitleOverride,
      String ogDescriptionOverride,
      String ogImageOverride,
      boolean passwordProtected,
      Integer maxViews,
      int viewCount,
      boolean statsPublic,
      List<String> tags) {}
}
