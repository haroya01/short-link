package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.user.domain.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * user 슬라이스의 {@link UserBlockChecker} 구현 — post 슬라이스의 댓글 생성 경로가 user 에 직접 의존하지 않고 차단 관계를 확인하게 한다.
 * 지금까지 저장만 되고 집행되지 않던 차단에 상호작용 시점 최소 집행을 붙이는 통로.
 */
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
