package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-delete contract: deleteAccount marks deleted_at, OAuth login restores within the grace
 * window, refresh fails for soft-deleted users, and the cleanup job hard-deletes when the cutoff
 * has passed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class SoftDeleteFlowTest {

  @Autowired private UserDeletionService deletionService;
  @Autowired private AuthService authService;
  @Autowired private SoftDeletedUserCleanupJob cleanupJob;
  @Autowired private UserRepository userRepository;

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
        ((AuthService.LoginResult.Tokens)
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
        ((AuthService.LoginResult.Tokens)
                authService.loginWithOAuth("sd3@local.test", "google", "g-sd3"))
            .issued();

    deletionService.deleteAccount(user.getId());

    assertThatThrownBy(() -> authService.refresh(initial.refreshToken()))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void cleanupJobHardDeletesPastGrace() {
    ReflectionTestUtils.setField(cleanupJob, "graceDays", 30L);

    UserEntity old = userRepository.save(new UserEntity("sd4@local.test", "google", "g-sd4"));
    deletionService.deleteAccount(old.getId());
    Instant longAgo = Instant.now().minus(Duration.ofDays(45));
    ReflectionTestUtils.setField(old, "deletedAt", longAgo);
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
    ReflectionTestUtils.setField(expired, "deletedAt", Instant.now().minus(Duration.ofDays(40)));
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
}
