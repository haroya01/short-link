package com.example.short_link.link.access.application;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralised "can this user read this link?" decision. Owner can always read; ADMIN can read any
 * link for support/observability. Mutation paths (register webhook, change destinations, edit OG)
 * still go through {@link LinkEntity#isOwnedBy} directly — admin is intentionally a read-only role
 * here, so they can never accidentally edit someone else's data.
 */
@Component
@RequiredArgsConstructor
public class LinkAccessGuard {

  private final UserAccessLookup users;

  public boolean canView(Long userId, LinkEntity link) {
    if (userId == null) return false;
    if (link.isOwnedBy(userId)) return true;
    return users.isAdmin(userId);
  }

  public void requireView(Long userId, LinkEntity link) {
    if (!canView(userId, link)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, link.getShortCode());
    }
  }
}
