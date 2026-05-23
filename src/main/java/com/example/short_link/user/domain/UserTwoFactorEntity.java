package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-user TOTP enrolment. Created in a pending state when the user starts setup; only flipped to
 * {@code enabled=true} once they successfully verify a code from their authenticator. Recovery
 * codes are stored as a JSON array of bcrypt hashes — single use, regenerable.
 */
@Entity
@Table(name = "user_two_factor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTwoFactorEntity extends BaseTimeEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(nullable = false, length = 512)
  private String secret;

  @Column(nullable = false)
  private boolean enabled = false;

  @Column(name = "recovery_codes", columnDefinition = "TEXT")
  private String recoveryCodes;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  public UserTwoFactorEntity(Long userId, String encryptedSecret) {
    this.userId = userId;
    this.secret = encryptedSecret;
    this.enabled = false;
  }

  public void rotateSecret(String encryptedSecret) {
    this.secret = encryptedSecret;
    this.enabled = false;
    this.recoveryCodes = null;
    this.lastUsedAt = null;
  }

  public void enable(String recoveryCodesJson) {
    this.enabled = true;
    this.recoveryCodes = recoveryCodesJson;
    this.lastUsedAt = Instant.now();
  }

  public void disable() {
    this.enabled = false;
    this.recoveryCodes = null;
    this.lastUsedAt = null;
  }

  public void replaceRecoveryCodes(String recoveryCodesJson) {
    this.recoveryCodes = recoveryCodesJson;
  }

  public void markUsed() {
    this.lastUsedAt = Instant.now();
  }
}
