package com.example.short_link.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

  public enum Role {
    USER,
    ADMIN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "oauth_provider", nullable = false, length = 32)
  private String oauthProvider;

  @Column(name = "oauth_id", nullable = false)
  private String oauthId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Role role = Role.USER;

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Seoul";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public UserEntity(String email, String oauthProvider, String oauthId) {
    this.email = email;
    this.oauthProvider = oauthProvider;
    this.oauthId = oauthId;
    this.role = Role.USER;
    this.timezone = "Asia/Seoul";
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  public void promoteToAdmin() {
    this.role = Role.ADMIN;
  }

  public void changeTimezone(String timezone) {
    this.timezone = timezone;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deletedAt = null;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }
}
