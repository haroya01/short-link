package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository {

  Optional<NotificationPreferenceEntity> findByUserIdAndType(
      Long userId, LinkNotificationType type);

  List<NotificationPreferenceEntity> findByUserId(Long userId);

  /** Atomically creates or updates one preference; concurrent writes retain exactly one row. */
  void setEnabled(Long userId, LinkNotificationType type, boolean enabled);
}
