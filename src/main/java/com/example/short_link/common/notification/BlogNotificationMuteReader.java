package com.example.short_link.common.notification;

/**
 * Fold an @-mention into a COMMENT/REPLY notice only when the recipient has not muted that kind.
 * Otherwise the explicit mention remains eligible, subject to its own MENTION preference at record
 * time.
 */
public interface BlogNotificationMuteReader {

  /** Whether {@code userId} has opted out of the given blog-bell kind (absent preference = on). */
  boolean isMuted(Long userId, BlogNotificationKind kind);
}
