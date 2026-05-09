package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

  @Autowired private AuthService authService;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenStore refreshStore;
  @Autowired private JwtTokenService jwt;

  @Test
  void loginWithOAuthCreatesNewUser() {
    IssuedTokens tokens =
        ((AuthService.LoginResult.Tokens)
                authService.loginWithOAuth("new@example.com", "google", "g-new"))
            .issued();

    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();
    UserEntity user = userRepository.findByOauthProviderAndOauthId("google", "g-new").orElseThrow();
    assertThat(user.getEmail()).isEqualTo("new@example.com");
    ParsedRefresh parsed = jwt.parseRefreshToken(tokens.refreshToken());
    assertThat(refreshStore.exists(parsed.userId(), parsed.jti())).isTrue();
  }

  @Test
  void loginWithOAuthReusesExistingUser() {
    UserEntity existing =
        userRepository.save(new UserEntity("existing@example.com", "google", "g-ex"));

    IssuedTokens tokens =
        ((AuthService.LoginResult.Tokens)
                authService.loginWithOAuth("existing@example.com", "google", "g-ex"))
            .issued();

    Long userId = jwt.parseAccessToken(tokens.accessToken());
    assertThat(userId).isEqualTo(existing.getId());
    assertThat(userRepository.findByOauthProviderAndOauthId("google", "g-ex"))
        .map(UserEntity::getId)
        .hasValue(existing.getId());
  }

  @Test
  void refreshRotatesToken() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken initial = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), initial.jti(), jwt.refreshTtl());

    IssuedTokens rotated = authService.refresh(initial.token());

    ParsedRefresh newParsed = jwt.parseRefreshToken(rotated.refreshToken());
    assertThat(newParsed.jti()).isNotEqualTo(initial.jti());
    assertThat(refreshStore.exists(user.getId(), initial.jti())).isFalse();
    assertThat(refreshStore.exists(user.getId(), newParsed.jti())).isTrue();
  }

  @Test
  void refreshThrowsForUnknownJti() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());

    assertThatThrownBy(() -> authService.refresh(refresh.token()))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void refreshThrowsForGarbageToken() {
    assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void refreshReuseWipesAllSessionsForUser() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-reuse"));
    RefreshToken initial = jwt.createRefreshToken(user.getId());
    RefreshToken otherSession = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), initial.jti(), jwt.refreshTtl());
    refreshStore.save(user.getId(), otherSession.jti(), jwt.refreshTtl());

    IssuedTokens rotated = authService.refresh(initial.token());
    ParsedRefresh rotatedParsed = jwt.parseRefreshToken(rotated.refreshToken());
    assertThat(refreshStore.exists(user.getId(), rotatedParsed.jti())).isTrue();
    assertThat(refreshStore.exists(user.getId(), otherSession.jti())).isTrue();

    assertThatThrownBy(() -> authService.refresh(initial.token()))
        .isInstanceOf(InvalidRefreshTokenException.class);

    assertThat(refreshStore.exists(user.getId(), rotatedParsed.jti())).isFalse();
    assertThat(refreshStore.exists(user.getId(), otherSession.jti())).isFalse();
  }

  @Test
  void logoutDeletesRefresh() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl());

    authService.logout(user.getId(), refresh.token());

    assertThat(refreshStore.exists(user.getId(), refresh.jti())).isFalse();
  }
}
