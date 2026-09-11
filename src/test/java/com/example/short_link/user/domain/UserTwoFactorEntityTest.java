package com.example.short_link.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserTwoFactorEntityTest {
  @Test
  void consumedHashCannotBeReusedAndOtherCodesRemainAvailable() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("first-hash", "second-hash"));
    Instant beforeUse = enrollment.getLastUsedAt();

    assertThat(enrollment.consumeRecoveryCode("first-hash")).isTrue();
    assertThat(enrollment.recoveryCodeHashes()).containsExactly("second-hash");
    assertThat(enrollment.getLastUsedAt()).isAfterOrEqualTo(beforeUse);
    Instant successfulUse = enrollment.getLastUsedAt();
    assertThat(enrollment.consumeRecoveryCode("first-hash")).isFalse();
    assertThat(enrollment.getLastUsedAt()).isEqualTo(successfulUse);
    assertThat(enrollment.consumeRecoveryCode("second-hash")).isTrue();
    assertThat(enrollment.recoveryCodeHashes()).isEmpty();
  }

  @Test
  void rejectedHashDoesNotChangeCodesOrLastUse() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("saved-hash"));
    Instant lastUse = enrollment.getLastUsedAt();

    assertThat(enrollment.consumeRecoveryCode("unknown-hash")).isFalse();
    assertThat(enrollment.recoveryCodeHashes()).containsExactly("saved-hash");
    assertThat(enrollment.getLastUsedAt()).isEqualTo(lastUse);
  }

  @Test
  void disabledEnrollmentCannotConsumeRecoveryCodes() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("saved-hash"));
    enrollment.disable();

    assertThat(enrollment.consumeRecoveryCode("saved-hash")).isFalse();
    assertThat(enrollment.getLastUsedAt()).isNull();
  }
}
