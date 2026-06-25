package com.example.short_link.notification.infrastructure.persistence;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkNotificationRepository extends JpaRepository<LinkNotificationEntity, Long> {
  List<LinkNotificationEntity> findByRecipientUserIdOrderByIdDesc(
      Long recipientUserId, Pageable pageable);

  List<LinkNotificationEntity> findByRecipientUserIdAndIdLessThanOrderByIdDesc(
      Long recipientUserId, Long beforeId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

  @Modifying
  @Query(
      "update LinkNotificationEntity n set n.readAt = :at "
          + "where n.id = :id and n.recipientUserId = :recipientUserId and n.readAt is null")
  int markRead(
      @Param("id") Long id,
      @Param("recipientUserId") Long recipientUserId,
      @Param("at") Instant at);

  @Modifying
  @Query(
      "update LinkNotificationEntity n set n.readAt = :at "
          + "where n.recipientUserId = :recipientUserId and n.readAt is null")
  int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("at") Instant at);
}
