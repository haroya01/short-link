package com.example.short_link.common.user;

// user 슬라이스가 구현하며, 댓글 생성 경로와의 순환 의존을 막는다.
public interface UserBlockChecker {

  boolean isBlocked(Long blockerId, Long blockedId);
}
