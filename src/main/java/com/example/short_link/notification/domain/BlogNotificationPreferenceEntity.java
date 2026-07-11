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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A user's opt-out for one blog-bell {@link NotificationType} (LIKE / COMMENT / FOLLOW / ...).
 * Absent row = enabled (default on); a row with {@code enabled=false} silences that type. One row
 * per (user, type) — see the unique key. Distinct from {@link NotificationPreferenceEntity}, which
 * opts out of link notifications ({@link LinkNotificationType}).
 */
@Entity
@Table(name = "blog_notification_preference")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogNotificationPreferenceEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  @Column(nullable = false)
  private boolean enabled;

  public BlogNotificationPreferenceEntity(Long userId, NotificationType type, boolean enabled) {
    this.userId = userId;
    this.type = type;
    this.enabled = enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
