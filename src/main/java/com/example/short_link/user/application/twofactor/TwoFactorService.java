package com.example.short_link.user.application.twofactor;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.domain.UserTwoFactorEntity;
import com.example.short_link.user.domain.UserTwoFactorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user TOTP enrolment and verification. Setup is two-step: a {@code start} call writes a
 * pending row with a fresh secret + provisioning URI, then {@code confirm(code)} flips it to
 * enabled and returns 10 single-use recovery codes (only shown once). Login verification accepts
 * either a current TOTP code or one of the recovery codes — recovery consumption rewrites the
 * stored hash list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

  public static final int RECOVERY_CODE_COUNT = 10;
  private static final int RECOVERY_CODE_LENGTH = 10;
  private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final UserTwoFactorRepository repository;
  private final SecretCipher cipher;
  private final MeterRegistry meterRegistry;
  private final TwoFactorProperties twofa;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  @Transactional(readOnly = true)
  public Status status(Long userId) {
    return repository
        .findById(userId)
        .map(row -> new Status(row.isEnabled(), row.getLastUsedAt()))
        .orElse(new Status(false, null));
  }

  @Transactional(readOnly = true)
  public boolean isEnabled(Long userId) {
    return repository.findById(userId).map(UserTwoFactorEntity::isEnabled).orElse(false);
  }

  /**
   * Begin enrolment. Writes a pending row (or rotates an existing pending one) and returns the
   * shared secret + otpauth provisioning URI. The user scans the URI in their authenticator and
   * then calls {@link #confirm}.
   */
  @Transactional
  public SetupChallenge start(Long userId) {
    UserEntity user =
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("user"));
    if (user.isDeleted()) throw new IllegalArgumentException("user deleted");

    UserTwoFactorEntity row = repository.findById(userId).orElse(null);
    if (row != null && row.isEnabled()) {
      throw new TwoFactorStateException("2FA is already enabled — disable first to re-enrol");
    }
    String plainSecret = TotpCodec.generateBase32Secret();
    String encrypted = cipher.encrypt(plainSecret);
    if (row == null) {
      row = new UserTwoFactorEntity(userId, encrypted);
      repository.save(row);
    } else {
      row.rotateSecret(encrypted);
    }
    return new SetupChallenge(
        plainSecret, TotpCodec.provisioningUri(twofa.issuer(), user.getEmail(), plainSecret));
  }

  @Transactional
  public List<String> confirm(Long userId, String code) {
    UserTwoFactorEntity row =
        repository
            .findById(userId)
            .orElseThrow(() -> new TwoFactorStateException("setup not started"));
    if (row.isEnabled()) throw new TwoFactorStateException("already enabled");
    String secret = cipher.decrypt(row.getSecret());
    if (!TotpCodec.verify(secret, code, Instant.now().getEpochSecond())) {
      throw new InvalidTotpCodeException();
    }
    List<String> plainCodes = generateRecoveryCodes();
    row.enable(joinHashes(hashAll(plainCodes)));
    meterRegistry.counter("twofa.enrolled").increment();
    return plainCodes;
  }

  /**
   * Verify a TOTP code during login. Returns true on success and stamps {@code last_used_at}.
   * Returns false on invalid code; caller decides response behaviour (rate limit, audit, etc).
   */
  @Transactional
  public boolean verify(Long userId, String code) {
    UserTwoFactorEntity row = repository.findById(userId).orElse(null);
    if (row == null || !row.isEnabled()) return false;
    String secret = cipher.decrypt(row.getSecret());
    if (!TotpCodec.verify(secret, code, Instant.now().getEpochSecond())) return false;
    row.markUsed();
    meterRegistry.counter("twofa.verify", "result", "code_ok").increment();
    return true;
  }

  /**
   * Consume a single-use recovery code. On success, rewrites the stored list with the matched hash
   * removed so the same code cannot be reused.
   */
  @Transactional
  public boolean verifyRecovery(Long userId, String recoveryCode) {
    UserTwoFactorEntity row = repository.findById(userId).orElse(null);
    if (row == null || !row.isEnabled() || row.getRecoveryCodes() == null) return false;
    List<String> hashes = readHashes(row.getRecoveryCodes());
    String trimmed = recoveryCode == null ? "" : recoveryCode.trim().toUpperCase();
    if (trimmed.isEmpty()) return false;
    int matchIdx = -1;
    for (int i = 0; i < hashes.size(); i++) {
      if (bcrypt.matches(trimmed, hashes.get(i))) {
        matchIdx = i;
        break;
      }
    }
    if (matchIdx < 0) return false;
    hashes.remove(matchIdx);
    row.replaceRecoveryCodes(joinHashes(hashes));
    row.markUsed();
    meterRegistry.counter("twofa.verify", "result", "recovery_ok").increment();
    return true;
  }

  @Transactional
  public void disable(Long userId, String code) {
    UserTwoFactorEntity row = repository.findById(userId).orElse(null);
    if (row == null || !row.isEnabled()) {
      throw new TwoFactorStateException("not enabled");
    }
    if (!verify(userId, code) && !verifyRecovery(userId, code)) {
      throw new InvalidTotpCodeException();
    }
    row.disable();
    meterRegistry.counter("twofa.disabled").increment();
  }

  @Transactional
  public List<String> regenerateRecoveryCodes(Long userId, String code) {
    UserTwoFactorEntity row = repository.findById(userId).orElse(null);
    if (row == null || !row.isEnabled()) {
      throw new TwoFactorStateException("not enabled");
    }
    if (!verify(userId, code)) {
      throw new InvalidTotpCodeException();
    }
    List<String> plain = generateRecoveryCodes();
    row.replaceRecoveryCodes(joinHashes(hashAll(plain)));
    meterRegistry.counter("twofa.recovery_codes_regenerated").increment();
    return plain;
  }

  private List<String> generateRecoveryCodes() {
    List<String> out = new ArrayList<>(RECOVERY_CODE_COUNT);
    for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
      StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH + 1);
      for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
        if (j == RECOVERY_CODE_LENGTH / 2) sb.append('-');
        sb.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
      }
      out.add(sb.toString());
    }
    return out;
  }

  private List<String> hashAll(List<String> plain) {
    List<String> out = new ArrayList<>(plain.size());
    for (String p : plain) out.add(bcrypt.encode(p));
    return out;
  }

  private List<String> readHashes(String stored) {
    if (stored == null || stored.isEmpty()) return new ArrayList<>();
    List<String> out = new ArrayList<>();
    for (String line : stored.split("\n")) {
      if (!line.isBlank()) out.add(line);
    }
    return out;
  }

  private String joinHashes(List<String> hashes) {
    return String.join("\n", hashes);
  }

  public record SetupChallenge(String secret, String provisioningUri) {}

  public record Status(boolean enabled, Instant lastUsedAt) {}
}
