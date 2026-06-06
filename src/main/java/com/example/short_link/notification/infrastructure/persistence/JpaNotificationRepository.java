package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.NotificationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, Long> {

  List<NotificationEntity> findByRecipientUserIdOrderByIdDesc(
      Long recipientUserId, Pageable pageable);

  List<NotificationEntity> findByRecipientUserIdAndIdLessThanOrderByIdDesc(
      Long recipientUserId, Long beforeId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :at "
          + "where n.id = :id and n.recipientUserId = :recipientUserId and n.readAt is null")
  int markRead(
      @Param("id") Long id,
      @Param("recipientUserId") Long recipientUserId,
      @Param("at") Instant at);

  @Modifying
  @Query(
      "update NotificationEntity n set n.readAt = :at "
          + "where n.recipientUserId = :recipientUserId and n.readAt is null")
  int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("at") Instant at);
}
