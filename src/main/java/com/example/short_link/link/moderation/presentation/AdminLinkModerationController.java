package com.example.short_link.link.moderation.presentation;

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
@RequestMapping("/api/v1/admin/links/{linkId}")
@RequiredArgsConstructor
public class AdminLinkModerationController {

  private final LinkModerationService linkModerationService;

  @PostMapping("/disable")
  public LinkModerationResponse disable(
      @AuthenticationPrincipal Long adminUserId, @PathVariable Long linkId) {
    boolean changed = linkModerationService.disable(linkId, LinkDisableReason.ADMIN, adminUserId);
    return new LinkModerationResponse(linkId, true, changed);
  }

  @PostMapping("/enable")
  public LinkModerationResponse enable(@PathVariable Long linkId) {
    boolean changed = linkModerationService.enable(linkId);
    return new LinkModerationResponse(linkId, false, changed);
  }
}
