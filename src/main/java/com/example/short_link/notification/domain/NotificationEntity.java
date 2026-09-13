package com.example.short_link.notification.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Actor identity is resolved at read time to keep display names current; target payload JSON is a
 * write-time snapshot. Null {@code readAt} means unread.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_user_id", nullable = false)
  private Long recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(columnDefinition = "json")
  private String payload;

  @Column(name = "read_at")
  private Instant readAt;

  public NotificationEntity(
      Long recipientUserId, NotificationType type, Long actorUserId, String payload) {
    this.recipientUserId = recipientUserId;
    this.type = type;
    this.actorUserId = actorUserId;
    this.payload = payload;
  }

  public boolean isRead() {
    return readAt != null;
  }
}
