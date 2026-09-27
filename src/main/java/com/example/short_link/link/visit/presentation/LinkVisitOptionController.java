package com.example.short_link.link.visit.presentation;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.application.LinkVisitOptionService;
import com.example.short_link.link.visit.presentation.request.LinkVisitOptionsRequest;
import com.example.short_link.link.visit.presentation.response.LinkVisitOptionsResponse;
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
public class LinkVisitOptionController {

  private final LinkVisitOptionService service;

  @PatchMapping("/{shortCode}/visit-options")
  public LinkVisitOptionsResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkVisitOptionsRequest request) {
    return LinkVisitOptionsResponse.from(
        shortCode,
        service.update(
            userId,
            shortCode,
            request.openInBrowser(),
            request.splash() == null ? null : request.splash().toChange(),
            request.opensAt(),
            Boolean.TRUE.equals(request.clearOpensAt())));
  }
}
