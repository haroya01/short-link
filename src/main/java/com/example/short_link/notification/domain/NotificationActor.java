package com.example.short_link.notification.domain;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.List;

// A remote account has no userId but a remoteId; its username is the full handle (name@domain)
// and profileUrl points at its home server. emojis draw the :shortcode: in a remote displayName.
public record NotificationActor(
    Long userId,
    String username,
    String avatarUrl,
    String profileUrl,
    Long remoteId,
    String displayName,
    List<CustomEmoji> emojis) {

  public NotificationActor {
    emojis = emojis == null ? List.of() : List.copyOf(emojis);
  }

  public NotificationActor(Long userId, String username, String avatarUrl) {
    this(userId, username, avatarUrl, null, null, null, List.of());
  }

  public NotificationActor(Long userId, String username, String avatarUrl, String profileUrl) {
    this(userId, username, avatarUrl, profileUrl, null, null, List.of());
  }

  public NotificationActor(
      Long userId, String username, String avatarUrl, String profileUrl, Long remoteId) {
    this(userId, username, avatarUrl, profileUrl, remoteId, null, List.of());
  }
}
