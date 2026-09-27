package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.BlogNotificationPreferenceEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBlogNotificationPreferenceRepository
    extends JpaRepository<BlogNotificationPreferenceEntity, Long> {

  Optional<BlogNotificationPreferenceEntity> findByUserIdAndType(
      Long userId, NotificationType type);

  List<BlogNotificationPreferenceEntity> findByUserId(Long userId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO blog_notification_preference (user_id, type, enabled, created_at) "
              + "VALUES (:userId, :type, :enabled, CURRENT_TIMESTAMP(6)) "
              + "ON DUPLICATE KEY UPDATE enabled = :enabled",
      nativeQuery = true)
  void setEnabled(
      @Param("userId") Long userId, @Param("type") String type, @Param("enabled") boolean enabled);

  @Query(
      "select p.userId from BlogNotificationPreferenceEntity p "
          + "where p.type = :type and p.enabled = false and p.userId in :userIds")
  List<Long> findDisabledUserIds(
      @Param("userIds") Collection<Long> userIds, @Param("type") NotificationType type);
}
