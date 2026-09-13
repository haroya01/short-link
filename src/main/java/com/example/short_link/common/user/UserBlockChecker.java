package com.example.short_link.common.user;

/** user 슬라이스가 구현하며, 댓글 생성 경로와의 순환 의존을 막는다. */
public interface UserBlockChecker {

  /** {@code blockerId} 가 {@code blockedId} 를 차단했는지 여부. 둘 중 하나라도 null 이면 false. */
  boolean isBlocked(Long blockerId, Long blockedId);
}
