package com.example.short_link.common.user;

// user 슬라이스가 구현하며, 콘텐츠 쓰기 경로와의 순환 의존을 막는다.
public interface UserModerationGuard {

  void requireCanWrite(Long userId);
}
