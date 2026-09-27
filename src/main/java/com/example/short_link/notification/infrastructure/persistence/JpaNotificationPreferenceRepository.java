package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.NotificationPreferenceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationPreferenceRepository
    extends JpaRepository<NotificationPreferenceEntity, Long> {

  Optional<NotificationPreferenceEntity> findByUserIdAndType(
      Long userId, LinkNotificationType type);

  List<NotificationPreferenceEntity> findByUserId(Long userId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO notification_preference (user_id, type, enabled, created_at) "
              + "VALUES (:userId, :type, :enabled, CURRENT_TIMESTAMP(6)) "
              + "ON DUPLICATE KEY UPDATE enabled = :enabled",
      nativeQuery = true)
  void setEnabled(
      @Param("userId") Long userId, @Param("type") String type, @Param("enabled") boolean enabled);
}
