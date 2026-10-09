package com.example.short_link.user.application.dto;

// sessionId is the login session the token was issued for; tokens minted outside a session carry
// none.
public record ParsedAccess(Long userId, String role, String sessionId) {

  public ParsedAccess(Long userId, String role) {
    this(userId, role, null);
  }
}
