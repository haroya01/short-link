package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNotificationPreferenceRepository
    extends JpaRepository<NotificationPreferenceEntity, Long> {

  Optional<NotificationPreferenceEntity> findByUserIdAndType(
      Long userId, LinkNotificationType type);

  List<NotificationPreferenceEntity> findByUserId(Long userId);
}
