package com.example.short_link.common.user;

/**
 * 중립 쓰기 게이트 — post 슬라이스(글·댓글 생성)가 user 슬라이스에 직접 의존하지 않고 제재 상태를 확인한다. {@code
 * common.user.UserModerationPort}(집행) 와 짝이며 슬라이스 그래프를 비순환으로 유지한다(ArchUnit 강제). 구현은 user 슬라이스가 제공한다.
 */
public interface UserModerationGuard {

  /**
   * 유저가 지금 콘텐츠를 생성할 수 있는지 확인하고, BANNED 이거나 만료 전 SUSPENDED 면 예외를 던진다. 정지 만료가 지난 SUSPENDED 는 통과(자동
   * 해제). {@code userId} 가 null(익명)이면 통과 — 이 게이트는 인증된 쓰기 경로에서만 쓴다.
   */
  void requireCanWrite(Long userId);
}
