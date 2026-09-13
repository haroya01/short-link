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

/** 익명(null)은 통과한다. BANNED 또는 만료 전 SUSPENDED 계정은 쓰기를 거부한다. */
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
