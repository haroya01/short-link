package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.user.domain.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class UserBlockCheckerAdapter implements UserBlockChecker {

  private final BlockRepository blockRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean isBlocked(Long blockerId, Long blockedId) {
    if (blockerId == null || blockedId == null) {
      return false;
    }
    return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
  }
}
