package com.example.short_link.notification.application.preference;

import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Notification-side implementation of the neutral {@link BlogNotificationMuteReader} port. Maps the
 * kernel {@link BlogNotificationKind} to this slice's {@link NotificationType} and delegates to
 * {@link BlogNotificationPreferenceService}, so the post slice can gate mention dedup on a mute
 * without a post→notification dependency.
 */
@Component
@RequiredArgsConstructor
class BlogNotificationMuteProvider implements BlogNotificationMuteReader {

  private final BlogNotificationPreferenceService preferenceService;

  @Override
  public boolean isMuted(Long userId, BlogNotificationKind kind) {
    return !preferenceService.isEnabled(userId, toType(kind));
  }

  private static NotificationType toType(BlogNotificationKind kind) {
    return switch (kind) {
      case COMMENT -> NotificationType.COMMENT;
      case REPLY -> NotificationType.REPLY;
    };
  }
}
