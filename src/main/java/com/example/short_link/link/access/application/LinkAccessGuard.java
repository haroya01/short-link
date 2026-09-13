package com.example.short_link.link.access.application;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** ADMIN access is read-only. Mutation paths must still require {@link LinkEntity#isOwnedBy}. */
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
