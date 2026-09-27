package com.example.short_link.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserTwoFactorEntityTest {
  private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

  @Test
  void consumedHashCannotBeReusedAndOtherCodesRemainAvailable() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("first-hash", "second-hash"), NOW);
    Instant beforeUse = enrollment.getLastUsedAt();

    assertThat(enrollment.consumeRecoveryCode("first-hash", NOW.plusSeconds(1))).isTrue();
    assertThat(enrollment.recoveryCodeHashes()).containsExactly("second-hash");
    assertThat(enrollment.getLastUsedAt()).isAfterOrEqualTo(beforeUse);
    Instant successfulUse = enrollment.getLastUsedAt();
    assertThat(enrollment.consumeRecoveryCode("first-hash", NOW.plusSeconds(1))).isFalse();
    assertThat(enrollment.getLastUsedAt()).isEqualTo(successfulUse);
    assertThat(enrollment.consumeRecoveryCode("second-hash", NOW.plusSeconds(1))).isTrue();
    assertThat(enrollment.recoveryCodeHashes()).isEmpty();
  }

  @Test
  void rejectedHashDoesNotChangeCodesOrLastUse() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("saved-hash"), NOW);
    Instant lastUse = enrollment.getLastUsedAt();

    assertThat(enrollment.consumeRecoveryCode("unknown-hash", NOW.plusSeconds(1))).isFalse();
    assertThat(enrollment.recoveryCodeHashes()).containsExactly("saved-hash");
    assertThat(enrollment.getLastUsedAt()).isEqualTo(lastUse);
  }

  @Test
  void disabledEnrollmentCannotConsumeRecoveryCodes() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("saved-hash"), NOW);
    enrollment.disable();

    assertThat(enrollment.consumeRecoveryCode("saved-hash", NOW.plusSeconds(1))).isFalse();
    assertThat(enrollment.getLastUsedAt()).isNull();
  }

  @Test
  void authenticationStepIsConsumedOnceButEnrollmentAndRecoveryDoNotConsumeIt() {
    UserTwoFactorEntity enrollment = new UserTwoFactorEntity(7L, "encrypted");
    enrollment.enable(List.of("recovery"), NOW);

    assertThat(enrollment.getLastVerifiedStep()).isNull();
    assertThat(enrollment.consumeRecoveryCode("recovery", NOW)).isTrue();
    assertThat(enrollment.consumeTotpStep(100L, NOW)).isTrue();
    assertThat(enrollment.consumeTotpStep(100L, NOW.plusSeconds(1))).isFalse();
    assertThat(enrollment.consumeTotpStep(99L, NOW.plusSeconds(1))).isFalse();
    assertThat(enrollment.getLastUsedAt()).isEqualTo(NOW);
    assertThat(enrollment.consumeTotpStep(101L, NOW.plusSeconds(30))).isTrue();
    assertThat(enrollment.getLastVerifiedStep()).isEqualTo(101L);
    enrollment.disable();
    assertThat(enrollment.consumeTotpStep(102L, NOW.plusSeconds(60))).isFalse();
    assertThat(enrollment.getLastVerifiedStep()).isNull();
  }
}
