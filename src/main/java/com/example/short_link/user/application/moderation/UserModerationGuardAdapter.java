package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * user 슬라이스의 {@link UserModerationGuard} 구현 — post 슬라이스의 글/댓글 생성 경로가 user 에 직접 의존하지 않고 제재 상태를 확인하게
 * 한다. BANNED 또는 만료 전 SUSPENDED 면 예외를 던진다. 익명(null)이면 통과.
 */
@Component
@RequiredArgsConstructor
class UserModerationGuardAdapter implements UserModerationGuard {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public void requireCanWrite(Long userId) {
    if (userId == null) {
      return;
    }
    UserEntity user = userRepository.findById(userId).orElse(null);
    if (user == null) {
      return; // 인증 주체가 실제로 존재하지 않으면 이 게이트가 판단할 몫이 아니다(상위에서 처리).
    }
    if (user.isBanned()) {
      throw new UserException(UserErrorCode.ACCOUNT_BANNED);
    }
    if (user.isSuspendedAt(Instant.now())) {
      throw new UserException(UserErrorCode.ACCOUNT_SUSPENDED);
    }
  }
}
