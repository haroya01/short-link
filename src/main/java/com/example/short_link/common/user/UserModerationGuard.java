package com.example.short_link.common.user;

/** user 슬라이스가 구현하며, 콘텐츠 쓰기 경로와의 순환 의존을 막는다. */
public interface UserModerationGuard {

  /**
   * 유저가 지금 콘텐츠를 생성할 수 있는지 확인하고, BANNED 이거나 만료 전 SUSPENDED 면 예외를 던진다. 정지 만료가 지난 SUSPENDED 는 통과(자동
   * 해제). {@code userId} 가 null(익명)이면 통과 — 이 게이트는 인증된 쓰기 경로에서만 쓴다.
   */
  void requireCanWrite(Long userId);
}
