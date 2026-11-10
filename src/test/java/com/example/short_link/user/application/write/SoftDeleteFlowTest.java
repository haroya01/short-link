package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.support.TestEntities;
import com.example.short_link.user.application.SessionChannel;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.properties.UserDeletionProperties;
import com.example.short_link.user.application.twofactor.TotpCodec;
import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import com.example.short_link.user.scheduler.SoftDeletedUserCleanupJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class SoftDeleteFlowTest {

  @Autowired private UserDeletionService deletionService;
  @Autowired private AuthService authService;
  @Autowired private SoftDeletedUserCleanupJob cleanupJob;
  @Autowired private UserRepository userRepository;
  @Autowired private TwoFactorService twoFactor;

  @Test
  void deleteAccountSetsDeletedAtAndKeepsRow() {
    UserEntity user = userRepository.save(new UserEntity("sd1@local.test", "google", "g-sd1"));
    deletionService.deleteAccount(user.getId());

    UserEntity reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.isDeleted()).isTrue();
    assertThat(reloaded.getDeletedAt()).isNotNull();
  }

  @Test
  void oauthLoginRestoresSoftDeletedUserWithinGrace() {
    UserEntity user = userRepository.save(new UserEntity("sd2@local.test", "google", "g-sd2"));
    deletionService.deleteAccount(user.getId());

    IssuedTokens tokens =
        ((AuthService.TokenLoginResult.Tokens)
                authService.loginWithOAuth("sd2@local.test", "google", "g-sd2"))
            .issued();
    assertThat(tokens.accessToken()).isNotBlank();

    UserEntity reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.isDeleted()).isFalse();
  }

  @Test
  void refreshRejectsSoftDeletedUser() {
    UserEntity user = userRepository.save(new UserEntity("sd3@local.test", "google", "g-sd3"));
    IssuedTokens initial =
        ((AuthService.TokenLoginResult.Tokens)
                authService.loginWithOAuth("sd3@local.test", "google", "g-sd3"))
            .issued();

    deletionService.deleteAccount(user.getId());

    assertThatThrownBy(() -> authService.refresh(initial.refreshToken(), SessionChannel.WEB))
        .isInstanceOf(UserException.class);
  }

  @Test
  void cleanupJobHardDeletesPastGrace() {
    ReflectionTestUtils.setField(cleanupJob, "userDeletion", new UserDeletionProperties(30L, true));

    UserEntity old = userRepository.save(new UserEntity("sd4@local.test", "google", "g-sd4"));
    deletionService.deleteAccount(old.getId());
    Instant longAgo = Instant.now().minus(Duration.ofDays(45));
    TestEntities.setField(old, "deletedAt", longAgo);
    userRepository.save(old);

    UserEntity recent = userRepository.save(new UserEntity("sd5@local.test", "google", "g-sd5"));
    deletionService.deleteAccount(recent.getId());

    cleanupJob.runDaily();

    assertThat(userRepository.findById(old.getId())).isEmpty();
    assertThat(userRepository.findById(recent.getId())).isPresent();
  }

  @Test
  void findTop200ByDeletedAtBeforePicksOnlyExpired() {
    UserEntity expired = userRepository.save(new UserEntity("sd6@local.test", "google", "g-sd6"));
    expired.softDelete();
    TestEntities.setField(expired, "deletedAt", Instant.now().minus(Duration.ofDays(40)));
    userRepository.save(expired);

    UserEntity withinGrace =
        userRepository.save(new UserEntity("sd7@local.test", "google", "g-sd7"));
    withinGrace.softDelete();
    userRepository.save(withinGrace);

    Instant cutoff = Instant.now().minus(Duration.ofDays(30));
    List<UserEntity> picked = userRepository.findTop200ByDeletedAtBefore(cutoff);

    assertThat(picked).extracting(UserEntity::getId).contains(expired.getId());
    assertThat(picked).extracting(UserEntity::getId).doesNotContain(withinGrace.getId());
  }

  @Test
  void deleteAccountIsIdempotent() {
    UserEntity user = userRepository.save(new UserEntity("sd8@local.test", "google", "g-sd8"));
    deletionService.deleteAccount(user.getId());
    Instant first = userRepository.findById(user.getId()).orElseThrow().getDeletedAt();

    deletionService.deleteAccount(user.getId());
    Instant second = userRepository.findById(user.getId()).orElseThrow().getDeletedAt();

    assertThat(second).isEqualTo(first);
  }

  @Test
  void aDeletedAccountWithTwoFactorStaysDeletedUntilTheSecondFactorPasses() {
    UserEntity user = userRepository.save(new UserEntity("sd9@local.test", "google", "g-sd9"));
    List<String> recovery = enableTwoFactor(user);
    deletionService.deleteAccount(user.getId());

    String challenge =
        ((AuthService.TokenLoginResult.TwoFactorRequired)
                authService.loginWithOAuth("sd9@local.test", "google", "g-sd9"))
            .challengeToken();
    assertThat(authService.loginWithOAuthMobile("sd9@local.test", "google", "g-sd9"))
        .isInstanceOf(AuthService.MobileLoginResult.TwoFactorRequired.class);
    assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();

    authService.completeTwoFactor(challenge, recovery.get(0), true, SessionChannel.WEB);
    assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isFalse();
  }

  @Test
  void appleSignInToADeletedAccountWithTwoFactorAlsoWaitsForTheSecondFactor() {
    UserEntity user = userRepository.save(new UserEntity("sd10@local.test", "apple", "apple-sd10"));
    enableTwoFactor(user);
    deletionService.deleteAccount(user.getId());

    assertThat(authService.loginWithApple("apple-sd10", "sd10@local.test", SessionChannel.MOBILE))
        .isInstanceOf(AuthService.TokenLoginResult.TwoFactorRequired.class);
    assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
  }

  @Test
  void anAccountDeletedWhileItsChallengeWasOpenIsStillRefused() {
    UserEntity user = userRepository.save(new UserEntity("sd11@local.test", "google", "g-sd11"));
    List<String> recovery = enableTwoFactor(user);
    String challenge =
        ((AuthService.TokenLoginResult.TwoFactorRequired)
                authService.loginWithOAuth("sd11@local.test", "google", "g-sd11"))
            .challengeToken();
    deletionService.deleteAccount(user.getId());

    assertThatThrownBy(
            () ->
                authService.completeTwoFactor(challenge, recovery.get(0), true, SessionChannel.WEB))
        .isInstanceOf(UserException.class);
    assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
  }

  private List<String> enableTwoFactor(UserEntity user) {
    TwoFactorService.SetupChallenge setup = twoFactor.start(user.getId());
    return twoFactor.confirm(
        user.getId(), TotpCodec.generateCode(setup.secret(), Instant.now().getEpochSecond() / 30));
  }
}
