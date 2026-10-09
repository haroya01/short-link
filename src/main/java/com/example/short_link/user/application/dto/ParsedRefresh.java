package com.example.short_link.user.application.dto;

// sessionId stays the same across rotations; refresh tokens issued before sessions existed carry
// none.
public record ParsedRefresh(Long userId, String jti, String sessionId) {

  public ParsedRefresh(Long userId, String jti) {
    this(userId, jti, null);
  }
}
