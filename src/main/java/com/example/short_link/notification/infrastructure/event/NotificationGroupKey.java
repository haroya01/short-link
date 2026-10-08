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
}
