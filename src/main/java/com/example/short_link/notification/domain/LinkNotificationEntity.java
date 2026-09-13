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

/** {@code readAt}가 null이면 안 읽은 알림이다. */
@Entity
@Table(name = "link_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkNotificationEntity extends BaseCreatedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_user_id", nullable = false)
  private Long recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LinkNotificationType type;

  @Column(name = "short_code", length = 64)
  private String shortCode;

  @Column(length = 200)
  private String subtitle;

  @Column(length = 500)
  private String body;

  @Column(name = "read_at")
  private Instant readAt;

  public LinkNotificationEntity(
      Long recipientUserId,
      LinkNotificationType type,
      String shortCode,
      String subtitle,
      String body) {
    this.recipientUserId = recipientUserId;
    this.type = type;
    this.shortCode = shortCode;
    this.subtitle = subtitle;
    this.body = body;
  }

  public boolean isRead() {
    return readAt != null;
  }
}
