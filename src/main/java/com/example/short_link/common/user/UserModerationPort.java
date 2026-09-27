package com.example.short_link.common.user;

import java.time.Instant;

// user 슬라이스가 구현한다. 신고 처리와 같은 트랜잭션에서 제재를 집행한다.
public interface UserModerationPort {

  void suspend(Long adminUserId, Long userId, Instant until);

  void ban(Long adminUserId, Long userId);
}
