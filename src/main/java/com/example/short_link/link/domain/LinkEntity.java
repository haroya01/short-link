package com.example.short_link.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
  private String originalUrl;

  @Column(name = "short_code", nullable = false, length = 16, unique = true)
  private String shortCode;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public LinkEntity(String originalUrl, String shortCode) {
    this(originalUrl, shortCode, null, null);
  }

  public LinkEntity(String originalUrl, String shortCode, Long userId, Instant expiresAt) {
    this.originalUrl = originalUrl;
    this.shortCode = shortCode;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }
}
