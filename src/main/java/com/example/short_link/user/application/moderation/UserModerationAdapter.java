package com.example.short_link.user.application.moderation;

import com.example.short_link.common.user.UserModerationPort;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 호출자 트랜잭션에서 제재를 집행한다. adminUserId는 감사용이며, 권한 검사는 관리자 API 보안 레이어가 담당한다. */
@Slf4j
@Component
@RequiredArgsConstructor
class UserModerationAdapter implements UserModerationPort {

  private final UserRepository userRepository;

  @Override
  @Transactional
  public void suspend(Long adminUserId, Long userId, Instant until) {
    if (until == null) {
      throw new IllegalArgumentException("suspend requires an expiry");
    }
    log.info("admin user suspend: adminUserId={}, userId={}, until={}", adminUserId, userId, until);
    UserEntity user = require(userId);
    user.suspend(until);
    userRepository.save(user);
  }

  @Override
  @Transactional
  public void ban(Long adminUserId, Long userId) {
    log.info("admin user ban: adminUserId={}, userId={}", adminUserId, userId);
    UserEntity user = require(userId);
    user.ban();
    userRepository.save(user);
  }

  private UserEntity require(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
  }
}
