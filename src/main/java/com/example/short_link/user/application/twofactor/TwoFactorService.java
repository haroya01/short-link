package com.example.short_link.user.application.twofactor;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserTwoFactorEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.UserTwoFactorRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoFactorService {

  public static final int RECOVERY_CODE_COUNT = RecoveryCodes.COUNT;

  private final UserRepository userRepository;
  private final UserTwoFactorRepository repository;
  private final SecretCipher cipher;
  private final MeterRegistry meterRegistry;
  private final TwoFactorProperties twofa;
  private final RecoveryCodes recoveryCodes;
  private final Clock clock;

  TwoFactorService(
      UserRepository userRepository,
      UserTwoFactorRepository repository,
      SecretCipher cipher,
      MeterRegistry meterRegistry,
      TwoFactorProperties twofa,
      RecoveryCodes recoveryCodes,
      Clock clock) {
    this.userRepository = userRepository;
    this.repository = repository;
    this.cipher = cipher;
    this.meterRegistry = meterRegistry;
    this.twofa = twofa;
    this.recoveryCodes = recoveryCodes;
    this.clock = clock;
  }

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

  @Transactional
  public SetupChallenge start(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) throw new UserException(UserErrorCode.USER_NOT_FOUND);

    UserTwoFactorEntity row = repository.findByIdForUpdate(userId).orElse(null);
    if (row != null && row.isEnabled()) {
      throw new UserException(
          UserErrorCode.TWO_FACTOR_STATE, "2FA is already enabled — disable first to re-enrol");
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
            .findByIdForUpdate(userId)
            .orElseThrow(
                () -> new UserException(UserErrorCode.TWO_FACTOR_STATE, "setup not started"));
    if (row.isEnabled()) throw new UserException(UserErrorCode.TWO_FACTOR_STATE, "already enabled");
    Instant now = clock.instant();
    String secret = cipher.decrypt(row.getSecret());
    if (!TotpCodec.verify(secret, code, now.getEpochSecond())) {
      throw new UserException(UserErrorCode.INVALID_TOTP);
    }
    List<String> plainCodes = recoveryCodes.generate();
    // Enrollment proves possession; only later authentications consume a time step.
    row.enable(recoveryCodes.hashAll(plainCodes), now);
    meterRegistry.counter("twofa.enrolled").increment();
    return plainCodes;
  }

  @Transactional
  public boolean verify(Long userId, String code) {
    UserTwoFactorEntity row = repository.findByIdForUpdate(userId).orElse(null);
    return row != null && verify(row, code);
  }

  private boolean verify(UserTwoFactorEntity row, String code) {
    if (!row.isEnabled()) return false;
    Instant now = clock.instant();
    String secret = cipher.decrypt(row.getSecret());
    OptionalLong step = TotpCodec.matchingStep(secret, code, now.getEpochSecond());
    if (step.isEmpty() || !row.consumeTotpStep(step.getAsLong(), now)) return false;
    meterRegistry.counter("twofa.verify", "result", "code_ok").increment();
    return true;
  }

  @Transactional
  public boolean verifyRecovery(Long userId, String recoveryCode) {
    UserTwoFactorEntity row = repository.findByIdForUpdate(userId).orElse(null);
    return row != null && verifyRecovery(row, recoveryCode);
  }

  private boolean verifyRecovery(UserTwoFactorEntity row, String code) {
    if (!row.isEnabled()) return false;
    String matchedHash = recoveryCodes.matchingHash(row.recoveryCodeHashes(), code).orElse(null);
    if (matchedHash == null || !row.consumeRecoveryCode(matchedHash, clock.instant())) return false;
    meterRegistry.counter("twofa.verify", "result", "recovery_ok").increment();
    return true;
  }

  @Transactional
  public void disable(Long userId, String code) {
    UserTwoFactorEntity row = repository.findByIdForUpdate(userId).orElse(null);
    if (row == null || !row.isEnabled()) {
      throw new UserException(UserErrorCode.TWO_FACTOR_STATE, "not enabled");
    }
    if (!verify(row, code) && !verifyRecovery(row, code)) {
      throw new UserException(UserErrorCode.INVALID_TOTP);
    }
    row.disable();
    meterRegistry.counter("twofa.disabled").increment();
  }

  @Transactional
  public List<String> regenerateRecoveryCodes(Long userId, String code) {
    UserTwoFactorEntity row = repository.findByIdForUpdate(userId).orElse(null);
    if (row == null || !row.isEnabled()) {
      throw new UserException(UserErrorCode.TWO_FACTOR_STATE, "not enabled");
    }
    if (!verify(row, code)) {
      throw new UserException(UserErrorCode.INVALID_TOTP);
    }
    List<String> plain = recoveryCodes.generate();
    row.replaceRecoveryCodes(recoveryCodes.hashAll(plain));
    meterRegistry.counter("twofa.recovery_codes_regenerated").increment();
    return plain;
  }

  public record SetupChallenge(String secret, String provisioningUri) {}

  public record Status(boolean enabled, Instant lastUsedAt) {}
}
