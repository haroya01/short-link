package com.example.short_link.common.user;

import java.time.Instant;

/**
 * 중립 집행 포트 — abuse(모더레이션) 슬라이스가 user 슬라이스에 직접 의존하지 않고 유저 제재를 집행한다. {@code
 * common.user.UserModerationGuard}(쓰기 게이트) 와 짝을 이루며 슬라이스 그래프를 비순환으로 유지한다(ArchUnit 강제). 구현은 user
 * 슬라이스가 제공하고 호출자 트랜잭션 안에서 실행된다.
 */
public interface UserModerationPort {

  /** 임시 정지 — {@code until} 까지 콘텐츠 생성 차단. 로그인은 허용. */
  void suspend(Long adminUserId, Long userId, Instant until);

  /** 영구 차단 — 로그인·콘텐츠 생성 모두 차단. */
  void ban(Long adminUserId, Long userId);
}
