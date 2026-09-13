package com.example.short_link.common.user;

import java.time.Instant;

/** user 슬라이스가 구현한다. 신고 처리와 같은 트랜잭션에서 제재를 집행한다. */
public interface UserModerationPort {

  /** 임시 정지 — {@code until} 까지 콘텐츠 생성 차단. 로그인은 허용. */
  void suspend(Long adminUserId, Long userId, Instant until);

  /** 영구 차단 — 로그인·콘텐츠 생성 모두 차단. */
  void ban(Long adminUserId, Long userId);
}
