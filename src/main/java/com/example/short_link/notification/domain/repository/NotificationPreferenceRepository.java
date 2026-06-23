package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import java.util.List;
import java.util.Optional;

/** Persistence port for per-user link-notification opt-outs. */
public interface NotificationPreferenceRepository {

  Optional<NotificationPreferenceEntity> findByUserIdAndType(
      Long userId, LinkNotificationType type);

  List<NotificationPreferenceEntity> findByUserId(Long userId);

  NotificationPreferenceEntity save(NotificationPreferenceEntity preference);
}
