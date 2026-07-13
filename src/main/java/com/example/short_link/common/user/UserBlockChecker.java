package com.example.short_link.common.user;

/**
 * 중립 조회 포트 — post 슬라이스(댓글 생성)가 user 슬라이스에 직접 의존하지 않고 차단 관계를 확인한다. 차단은 지금까지 저장만 되고 서버에서 집행되지
 * 않았다(클라이언트 필터 전용). 이 포트로 상호작용 시점에 최소 집행을 붙인다. 구현은 user 슬라이스가 제공한다.
 */
public interface UserBlockChecker {

  /** {@code blockerId} 가 {@code blockedId} 를 차단했는지 여부. 둘 중 하나라도 null 이면 false. */
  boolean isBlocked(Long blockerId, Long blockedId);
}
