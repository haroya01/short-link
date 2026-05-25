package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralised "can this user read this link?" decision. Owner can always read; ADMIN can read any
 * link for support/observability. Mutation paths (register webhook, change destinations, edit OG)
 * still go through {@link LinkEntity#isOwnedBy} directly — admin is intentionally a read-only role
 * here, so they can never accidentally edit someone else's data.
 */
@Component
@RequiredArgsConstructor
public class LinkAccessGuard {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public boolean canView(Long userId, LinkEntity link) {
    if (userId == null) return false;
    if (link.isOwnedBy(userId)) return true;
    return userRepository.findById(userId).map(UserEntity::isAdmin).orElse(false);
  }

  public void requireView(Long userId, LinkEntity link) {
    if (!canView(userId, link)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, link.getShortCode());
    }
  }
}
