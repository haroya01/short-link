package com.example.short_link.profile.domain;

import com.example.short_link.profile.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 변경일부터 30일 동안 이전 이름을 새 이름으로 리다이렉트하고 타인의 선점을 막는다. 만료 후에는 이전 이름을 다시 사용할 수 있다. */
@Entity
@Table(name = "username_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsernameHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "old_username", nullable = false, length = 32)
  private String oldUsername;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public UsernameHistoryEntity(Long userId, String oldUsername, Instant expiresAt) {
    this.userId = userId;
    this.oldUsername = oldUsername.toLowerCase(Locale.ROOT);
    this.changedAt = Instant.now();
    this.expiresAt = expiresAt;
  }
}
