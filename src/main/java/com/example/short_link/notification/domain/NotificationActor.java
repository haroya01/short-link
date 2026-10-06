package com.example.short_link.notification.domain;

// A remote account has no userId; its username is the full handle (name@domain) and profileUrl
// points at its home server.
public record NotificationActor(Long userId, String username, String avatarUrl, String profileUrl) {

  public NotificationActor(Long userId, String username, String avatarUrl) {
    this(userId, username, avatarUrl, null);
  }
}
