package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, Long> {

  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :at "
          + "where n.recipientUserId = :recipientUserId and n.readAt is null and n.filtered = false")
  int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("at") Instant at);

  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :at "
          + "where n.recipientUserId = :recipientUserId and n.readAt is null and n.filtered = false"
          + " and n.type in :types")
  int markAllReadOfTypes(
      @Param("recipientUserId") Long recipientUserId,
      @Param("types") Collection<NotificationType> types,
      @Param("at") Instant at);
}
