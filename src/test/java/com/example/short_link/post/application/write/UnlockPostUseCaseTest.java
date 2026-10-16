package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostGrants;
import com.example.short_link.post.application.access.PostPasswordAttempts;
import com.example.short_link.post.application.access.PostPasswords;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnlockPostUseCaseTest {

  private static final String IP = "203.0.113.9";
  private static final String PASSWORD = "비밀번호는 길어도 된다";

  private final UserRepository users = mock(UserRepository.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final PostPasswordAttempts attempts = mock(PostPasswordAttempts.class);
  private final PostPasswords passwords = new PostPasswords();
  private final PostGrants grants = new PostGrants("secret", Clock.systemUTC());
  private PostEntity post;
  private UnlockPostUseCase useCase;

  @BeforeEach
  void aPasswordPostByJohn() {
    UserEntity john = TestEntities.withId(new UserEntity("j@x.com", "google", "g-1"), 7L);
    john.claimUsername("john");
    when(users.findByUsername("john")).thenReturn(Optional.of(john));
    post = TestEntities.withId(new PostEntity(7L, "secret", "Secret", "ko"), 42L);
    post.publish();
    post.protectWith(passwords.hash(PASSWORD));
    post.changeAudience(PostAudience.PASSWORD, "unused");
    when(posts.findUnblockedByUserIdAndSlug(eq(7L), eq("secret"), any()))
        .thenReturn(Optional.of(post));
    when(attempts.tryAttempt(42L, IP)).thenReturn(true);
    useCase = useCase("KR");
  }

  private UnlockPostUseCase useCase(String readerCountry) {
    PostAccessPolicy policy =
        PostAccessFixtures.policy(mock(FollowRepository.class), readerCountry);
    return new UnlockPostUseCase(users, posts, policy, passwords, attempts, grants);
  }

  @Test
  void theRightPasswordHandsOutAGrantThatOpensThePost() {
    PostGrants.Grant grant = useCase.unlock(" JOHN ", "secret", PASSWORD, reader(null));

    assertThat(grants.verify(post, grant.grant())).isTrue();
    assertThat(grant.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(23 * 3600));
    verify(attempts).reset(42L, IP);
  }

  @Test
  void aWrongPasswordIsRefusedAndCounted() {
    assertCode(
        () -> useCase.unlock("john", "secret", "not it", reader(null)),
        PostErrorCode.POST_PASSWORD_INVALID);
    verify(attempts).tryAttempt(42L, IP);
    verify(attempts, never()).reset(anyLong(), anyString());
  }

  @Test
  void tooManyAttemptsTellWhenToTryAgain() {
    when(attempts.tryAttempt(42L, IP)).thenReturn(false);
    when(attempts.retryAfterSeconds(42L, IP)).thenReturn(321L);

    assertThatThrownBy(() -> useCase.unlock("john", "secret", PASSWORD, reader(null)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_PASSWORD_ATTEMPTS_EXCEEDED);
              assertThat(e.properties()).containsEntry("retryAfterSeconds", 321L);
            });
  }

  @Test
  void aBlockedCountryIsRefusedWithoutSpendingAnAttempt() {
    post.blockCountries(List.of("JP"));

    assertThatThrownBy(() -> useCase("JP").unlock("john", "secret", PASSWORD, reader(null)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_LOCKED);
              assertThat(e.properties()).containsEntry("reason", PostLock.REGION);
            });
    verify(attempts, never()).tryAttempt(anyLong(), any());
  }

  @Test
  void onlyAPublishedPasswordPostCanBeUnlocked() {
    post.changeAudience(PostAudience.FOLLOWERS, null);
    assertCode(
        () -> useCase.unlock("john", "secret", PASSWORD, reader(null)),
        PostErrorCode.POST_NOT_FOUND);

    post.changeAudience(PostAudience.PASSWORD, null);
    post.unpublish();
    assertCode(
        () -> useCase.unlock("john", "secret", PASSWORD, reader(null)),
        PostErrorCode.POST_NOT_FOUND);
    verify(attempts, never()).tryAttempt(anyLong(), any());
  }

  @Test
  void anUnknownAuthorOrPostIsNotFound() {
    assertThatThrownBy(() -> useCase.unlock("nobody", "secret", PASSWORD, reader(null)))
        .isInstanceOf(ProfileException.class);
    assertCode(
        () -> useCase.unlock("john", "missing", PASSWORD, reader(null)),
        PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void theAuthorCanCheckTheirOwnPassword() {
    assertThat(useCase.unlock("john", "secret", PASSWORD, reader(7L)).grant()).isNotBlank();
  }

  private static PostReader reader(Long viewerId) {
    return new PostReader(viewerId, null, "ignored-grant", IP, false);
  }

  private static void assertCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, PostErrorCode code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            PostException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
  }
}
