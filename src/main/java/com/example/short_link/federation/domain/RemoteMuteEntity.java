package com.example.short_link.federation.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_remote_mute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RemoteMuteEntity extends BaseCreatedEntity {

  public static final long MIN_DURATION_SECONDS = 60;
  public static final long MAX_DURATION_SECONDS = 365L * 24 * 3600;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "remote_actor_id", nullable = false)
  private Long remoteActorId;

  @Column(name = "hide_notifications", nullable = false)
  private boolean hideNotifications;

  @Column(name = "expires_at")
  private Instant expiresAt;

  public RemoteMuteEntity(
      Long userId, Long remoteActorId, boolean hideNotifications, Instant expiresAt) {
    this.userId = userId;
    this.remoteActorId = remoteActorId;
    this.hideNotifications = hideNotifications;
    this.expiresAt = expiresAt;
  }

  public void change(boolean hideNotifications, Instant expiresAt) {
    this.hideNotifications = hideNotifications;
    this.expiresAt = expiresAt;
  }
}
