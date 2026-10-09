package com.example.short_link.notification.infrastructure.event;

import com.example.short_link.notification.domain.NotificationType;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class NotificationGroupKey {

  private NotificationGroupKey() {}

  static String of(NotificationType type, Long subjectId) {
    return type.grouped()
        ? type.name() + ":" + subjectId + ":" + LocalDate.now(ZoneOffset.UTC)
        : null;
  }

  // One notice per person, subject and day, keyed by the actor too so the inbox never folds
  // different people into one row: following, unfollowing and following again says it once.
  static String perActor(NotificationType type, Long actorId, Long subjectId) {
    return type.name() + ":" + actorId + ":" + subjectId + ":" + LocalDate.now(ZoneOffset.UTC);
  }
}
