package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_mute",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_mute",
            columnNames = {"user_id", "muted_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMuteEntity extends BaseCreatedEntity {

  public static final long MAX_DURATION_SECONDS = 365L * 24 * 3600;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "muted_user_id", nullable = false)
  private Long mutedUserId;

  @Column(name = "hide_notifications", nullable = false)
  private boolean hideNotifications;

  @Column(name = "expires_at")
  private Instant expiresAt;

  public UserMuteEntity(
      Long userId, Long mutedUserId, boolean hideNotifications, Instant expiresAt) {
    this.userId = userId;
    this.mutedUserId = mutedUserId;
    this.hideNotifications = hideNotifications;
    this.expiresAt = expiresAt;
  }

  public void change(boolean hideNotifications, Instant expiresAt) {
    this.hideNotifications = hideNotifications;
    this.expiresAt = expiresAt;
  }

  public boolean activeAt(Instant now) {
    return expiresAt == null || expiresAt.isAfter(now);
  }
}
