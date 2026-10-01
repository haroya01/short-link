package com.example.short_link.link.moderation.presentation;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.link.moderation.presentation.response.LinkModerationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/links/{code}")
@RequiredArgsConstructor
public class AdminLinkModerationController {

  private final LinkModerationService linkModerationService;

  @PostMapping("/disable")
  public LinkModerationResponse disable(
      @AuthenticationPrincipal Long adminUserId, @PathVariable ShortCode code) {
    boolean changed = linkModerationService.disable(code, LinkDisableReason.ADMIN, adminUserId);
    return new LinkModerationResponse(code.value(), true, changed);
  }

  @PostMapping("/enable")
  public LinkModerationResponse enable(@PathVariable ShortCode code) {
    boolean changed = linkModerationService.enable(code);
    return new LinkModerationResponse(code.value(), false, changed);
  }
}
