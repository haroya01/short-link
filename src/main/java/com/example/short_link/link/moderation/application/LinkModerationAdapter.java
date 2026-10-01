package com.example.short_link.link.moderation.application;

import com.example.short_link.common.link.LinkModerationPort;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkModerationAdapter implements LinkModerationPort {

  private final LinkModerationService linkModerationService;

  @Override
  public void disable(Long adminUserId, Long linkId) {
    linkModerationService.disable(linkId, LinkDisableReason.ABUSE_REPORT, adminUserId);
  }
}
