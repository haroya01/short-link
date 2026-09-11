package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.user.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-user TOTP enrolment. Created in a pending state when the user starts setup; only flipped to
 * {@code enabled=true} once they successfully verify a code from their authenticator. Recovery
 * codes are stored as newline-separated bcrypt hashes — single use, regenerable.
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

  public void enable(List<String> recoveryCodeHashes) {
    this.enabled = true;
    this.recoveryCodes = String.join("\n", recoveryCodeHashes);
    this.lastUsedAt = Instant.now();
  }

  public void disable() {
    this.enabled = false;
    this.recoveryCodes = null;
    this.lastUsedAt = null;
  }

  public void replaceRecoveryCodes(List<String> recoveryCodeHashes) {
    this.recoveryCodes = String.join("\n", recoveryCodeHashes);
  }

  public List<String> recoveryCodeHashes() {
    if (recoveryCodes == null || recoveryCodes.isEmpty()) return List.of();
    return Arrays.stream(recoveryCodes.split("\n")).filter(line -> !line.isBlank()).toList();
  }

  /** Removes one matched hash and records its use as a single state change. */
  public boolean consumeRecoveryCode(String matchedHash) {
    if (!enabled) return false;
    List<String> remaining = new ArrayList<>(recoveryCodeHashes());
    if (!remaining.remove(matchedHash)) return false;
    recoveryCodes = String.join("\n", remaining);
    markUsed();
    return true;
  }

  public void markUsed() {
    this.lastUsedAt = Instant.now();
  }
}
