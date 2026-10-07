package com.example.short_link.notification.domain;

// A remote account has no userId but a remoteId; its username is the full handle (name@domain)
// and profileUrl points at its home server.
public record NotificationActor(
    Long userId, String username, String avatarUrl, String profileUrl, Long remoteId) {

  public NotificationActor(Long userId, String username, String avatarUrl) {
    this(userId, username, avatarUrl, null, null);
  }

  public NotificationActor(Long userId, String username, String avatarUrl, String profileUrl) {
    this(userId, username, avatarUrl, profileUrl, null);
  }
}
