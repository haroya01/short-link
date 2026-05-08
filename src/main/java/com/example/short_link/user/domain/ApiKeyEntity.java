package com.example.short_link.user.domain;

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
@Table(name = "api_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKeyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "key_prefix", nullable = false, length = 20)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, length = 64)
  private String keyHash;

  @Column(length = 100)
  private String name;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public ApiKeyEntity(Long userId, String keyPrefix, String keyHash, String name) {
    this.userId = userId;
    this.keyPrefix = keyPrefix;
    this.keyHash = keyHash;
    this.name = name;
    this.createdAt = Instant.now();
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public void markUsed(Instant when) {
    this.lastUsedAt = when;
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }
}
