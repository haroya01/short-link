package com.example.short_link.user.application.twofactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TwoFactorServiceTest {

  @Autowired private TwoFactorService service;
  @Autowired private UserRepository userRepository;

  @Test
  void enrollFlow() {
    UserEntity user = userRepository.save(new UserEntity("tf1@local.test", "google", "g-tf1"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());

    assertThat(challenge.secret()).isNotBlank();
    assertThat(challenge.provisioningUri()).startsWith("otpauth://totp/");
    assertThat(service.isEnabled(user.getId())).isFalse();

    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    List<String> recovery = service.confirm(user.getId(), code);
    assertThat(recovery).hasSize(TwoFactorService.RECOVERY_CODE_COUNT);
    assertThat(service.isEnabled(user.getId())).isTrue();
  }

  @Test
  void confirmRejectsWrongCode() {
    UserEntity user = userRepository.save(new UserEntity("tf2@local.test", "google", "g-tf2"));
    service.start(user.getId());
    assertThatThrownBy(() -> service.confirm(user.getId(), "000000"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void verifyRecoveryConsumesCodeOnce() {
    UserEntity user = userRepository.save(new UserEntity("tf3@local.test", "google", "g-tf3"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String setupCode =
        TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    List<String> recovery = service.confirm(user.getId(), setupCode);

    String oneCode = recovery.get(0);
    assertThat(service.verifyRecovery(user.getId(), oneCode)).isTrue();
    assertThat(service.verifyRecovery(user.getId(), oneCode)).isFalse();
    assertThat(service.verifyRecovery(user.getId(), recovery.get(1))).isTrue();
  }

  @Test
  void disableRequiresValidCode() {
    UserEntity user = userRepository.save(new UserEntity("tf4@local.test", "google", "g-tf4"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String setupCode =
        TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), setupCode);

    assertThatThrownBy(() -> service.disable(user.getId(), "000000"))
        .isInstanceOf(UserException.class);
    assertThat(service.isEnabled(user.getId())).isTrue();

    String disableCode =
        TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.disable(user.getId(), disableCode);
    assertThat(service.isEnabled(user.getId())).isFalse();
  }

  @Test
  void startRejectsWhenAlreadyEnabled() {
    UserEntity user = userRepository.save(new UserEntity("tf5@local.test", "google", "g-tf5"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);

    assertThatThrownBy(() -> service.start(user.getId())).isInstanceOf(UserException.class);
  }

  @Test
  void statusReturnsDisabledForUserWithoutRecord() {
    UserEntity user = userRepository.save(new UserEntity("tfn@local.test", "google", "g-tfn"));
    TwoFactorService.Status status = service.status(user.getId());
    assertThat(status.enabled()).isFalse();
    assertThat(status.lastUsedAt()).isNull();
  }

  @Test
  void statusReportsEnabledWithLastUsedAfterVerify() {
    UserEntity user = userRepository.save(new UserEntity("tfs@local.test", "google", "g-tfs"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);
    String verifyCode =
        TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    assertThat(service.verify(user.getId(), verifyCode)).isTrue();

    TwoFactorService.Status status = service.status(user.getId());
    assertThat(status.enabled()).isTrue();
    assertThat(status.lastUsedAt()).isNotNull();
  }

  @Test
  void confirmRejectsWhenAlreadyEnabled() {
    UserEntity user = userRepository.save(new UserEntity("tfa@local.test", "google", "g-tfa"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);
    String code2 = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);

    assertThatThrownBy(() -> service.confirm(user.getId(), code2))
        .isInstanceOf(UserException.class);
  }

  @Test
  void confirmRejectsWhenSetupNotStarted() {
    UserEntity user = userRepository.save(new UserEntity("tfx@local.test", "google", "g-tfx"));
    assertThatThrownBy(() -> service.confirm(user.getId(), "123456"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void verifyReturnsFalseWhenNotEnabled() {
    UserEntity user = userRepository.save(new UserEntity("tfv@local.test", "google", "g-tfv"));
    assertThat(service.verify(user.getId(), "123456")).isFalse();
  }

  @Test
  void verifyRecoveryReturnsFalseForBlankCode() {
    UserEntity user = userRepository.save(new UserEntity("tfb@local.test", "google", "g-tfb"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);

    assertThat(service.verifyRecovery(user.getId(), "   ")).isFalse();
    assertThat(service.verifyRecovery(user.getId(), null)).isFalse();
    assertThat(service.verifyRecovery(user.getId(), "NOT-A-REAL-CODE")).isFalse();
  }

  @Test
  void disableThrowsWhenNotEnabled() {
    UserEntity user = userRepository.save(new UserEntity("tfd@local.test", "google", "g-tfd"));
    assertThatThrownBy(() -> service.disable(user.getId(), "123456"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void disableAcceptsRecoveryCodeAsAlternative() {
    UserEntity user = userRepository.save(new UserEntity("tfr@local.test", "google", "g-tfr"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    List<String> recovery = service.confirm(user.getId(), code);

    service.disable(user.getId(), recovery.get(0));
    assertThat(service.isEnabled(user.getId())).isFalse();
  }

  @Test
  void disableRejectsWhenBothTotpAndRecoveryFail() {
    UserEntity user = userRepository.save(new UserEntity("tfdf@local.test", "google", "g-tfdf"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);

    assertThatThrownBy(() -> service.disable(user.getId(), "NEVER-MATCHES"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void startRotatesSecretWhenPendingRowExists() {
    UserEntity user = userRepository.save(new UserEntity("tfrot@local.test", "google", "g-tfrot"));
    TwoFactorService.SetupChallenge first = service.start(user.getId());
    TwoFactorService.SetupChallenge second = service.start(user.getId());

    assertThat(second.secret()).isNotEqualTo(first.secret());
  }

  @Test
  void verifyReturnsFalseForInvalidCode() {
    UserEntity user = userRepository.save(new UserEntity("tfvi@local.test", "google", "g-tfvi"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);

    assertThat(service.verify(user.getId(), "000000")).isFalse();
  }

  @Test
  void regenerateRequiresEnabledAndValidCode() {
    UserEntity user = userRepository.save(new UserEntity("tfg@local.test", "google", "g-tfg"));
    assertThatThrownBy(() -> service.regenerateRecoveryCodes(user.getId(), "000000"))
        .isInstanceOf(UserException.class);

    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    service.confirm(user.getId(), code);

    assertThatThrownBy(() -> service.regenerateRecoveryCodes(user.getId(), "000000"))
        .isInstanceOf(UserException.class);
  }

  @Test
  void regenerateRecoveryCodesReplacesOldOnes() {
    UserEntity user = userRepository.save(new UserEntity("tf6@local.test", "google", "g-tf6"));
    TwoFactorService.SetupChallenge challenge = service.start(user.getId());
    String code = TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    List<String> first = service.confirm(user.getId(), code);

    String regenCode =
        TotpCodec.generateCode(challenge.secret(), Instant.now().getEpochSecond() / 30);
    List<String> second = service.regenerateRecoveryCodes(user.getId(), regenCode);
    assertThat(second).doesNotContainAnyElementsOf(first);
    assertThat(service.verifyRecovery(user.getId(), first.get(0))).isFalse();
    assertThat(service.verifyRecovery(user.getId(), second.get(0))).isTrue();
  }
}
