package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.SessionChannel;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

  @Autowired private AuthService authService;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenStore refreshStore;
  @Autowired private JwtTokenService jwt;
  @Autowired private DeviceTokenCommandService devices;
  @Autowired private DeviceTokenRepository deviceTokens;
  @Autowired private StringRedisTemplate redis;
  @Autowired private JsonMapper json;

  @Test
  void aSessionKeepsItsIdAcrossRotationsAndTakesItsDevicesWithItWhenItEnds() {
    IssuedTokens login =
        ((AuthService.TokenLoginResult.Tokens)
                authService.loginWithOAuth("sess@example.com", "google", "g-sess"))
            .issued();
    String sessionId = jwt.parseAccessTokenDetailed(login.accessToken()).sessionId();
    assertThat(sessionId).isNotBlank();
    assertThat(jwt.parseRefreshToken(login.refreshToken()).sessionId()).isEqualTo(sessionId);
    Long userId = jwt.parseAccessTokenDetailed(login.accessToken()).userId();
    devices.register(userId, "sess-phone", "ios", null, sessionId);
    devices.register(userId, "sess-old-install", "ios", null, null);

    IssuedTokens rotated = authService.refresh(login.refreshToken(), SessionChannel.WEB);

    assertThat(jwt.parseRefreshToken(rotated.refreshToken()).sessionId()).isEqualTo(sessionId);
    assertThat(jwt.parseAccessTokenDetailed(rotated.accessToken()).sessionId())
        .isEqualTo(sessionId);
    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .extracting(DeviceTarget::token)
        .containsExactlyInAnyOrder("sess-phone", "sess-old-install");

    authService.logout(rotated.refreshToken());

    assertThat(deviceTokens.targetsForUser(userId, Instant.now()))
        .extracting(DeviceTarget::token)
        .containsExactly("sess-old-install");
  }

  @Test
  void aRefreshTokenFromBeforeSessionsStartsOne() {
    UserEntity user = userRepository.save(new UserEntity("legacy@example.com", "google", "g-leg"));
    RefreshToken legacy = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), legacy.jti(), jwt.refreshTtl(SessionChannel.WEB));
    assertThat(jwt.parseRefreshToken(legacy.token()).sessionId()).isNull();

    IssuedTokens rotated = authService.refresh(legacy.token(), SessionChannel.WEB);

    String sessionId = jwt.parseRefreshToken(rotated.refreshToken()).sessionId();
    assertThat(sessionId).isNotBlank();
    assertThat(jwt.parseAccessTokenDetailed(rotated.accessToken()).sessionId())
        .isEqualTo(sessionId);
  }

  @Test
  void loginWithOAuthCreatesNewUser() {
    IssuedTokens tokens =
        ((AuthService.TokenLoginResult.Tokens)
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
        ((AuthService.TokenLoginResult.Tokens)
                authService.loginWithOAuth("existing@example.com", "google", "g-ex"))
            .issued();

    Long userId = jwt.parseAccessTokenDetailed(tokens.accessToken()).userId();
    assertThat(userId).isEqualTo(existing.getId());
    assertThat(userRepository.findByOauthProviderAndOauthId("google", "g-ex"))
        .map(UserEntity::getId)
        .hasValue(existing.getId());
  }

  @Test
  void refreshRotatesToken() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken initial = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), initial.jti(), jwt.refreshTtl(SessionChannel.WEB));

    IssuedTokens rotated = authService.refresh(initial.token(), SessionChannel.WEB);

    ParsedRefresh newParsed = jwt.parseRefreshToken(rotated.refreshToken());
    assertThat(newParsed.jti()).isNotEqualTo(initial.jti());
    assertThat(refreshStore.exists(user.getId(), initial.jti())).isFalse();
    assertThat(refreshStore.exists(user.getId(), newParsed.jti())).isTrue();
  }

  @Test
  void refreshThrowsForUnknownJti() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);

    assertThatThrownBy(() -> authService.refresh(refresh.token(), SessionChannel.WEB))
        .isInstanceOf(UserException.class);
  }

  @Test
  void refreshThrowsForGarbageToken() {
    assertThatThrownBy(() -> authService.refresh("not-a-jwt", SessionChannel.WEB))
        .isInstanceOf(UserException.class);
  }

  @Test
  void refreshWithUnknownTokenPastGraceRejectsTokenButKeepsOtherSessions() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-theft"));
    RefreshToken otherSession = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), otherSession.jti(), jwt.refreshTtl(SessionChannel.WEB));
    // A token the server has no live session for and never just rotated (no grace marker) — a stale
    // or replayed token. It must be rejected, but the user's OTHER live sessions stay intact:
    // wiping everything over one stale token logged the owner out on every device.
    RefreshToken stale = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);

    assertThatThrownBy(() -> authService.refresh(stale.token(), SessionChannel.WEB))
        .isInstanceOf(UserException.class);

    assertThat(refreshStore.exists(user.getId(), otherSession.jti())).isTrue();
  }

  @Test
  void replayWithinRotationGraceReissuesWithoutWipingSessions() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-grace"));
    RefreshToken initial = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    RefreshToken otherSession = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), initial.jti(), jwt.refreshTtl(SessionChannel.WEB));
    refreshStore.save(user.getId(), otherSession.jti(), jwt.refreshTtl(SessionChannel.WEB));

    IssuedTokens rotated = authService.refresh(initial.token(), SessionChannel.WEB);
    ParsedRefresh rotatedParsed = jwt.parseRefreshToken(rotated.refreshToken());

    // Stale replay of the just-rotated token (the cross-tab/subdomain race) within the grace
    // window must NOT throw and must NOT wipe sessions — it re-issues a fresh pair instead.
    IssuedTokens reissued = authService.refresh(initial.token(), SessionChannel.WEB);
    assertThat(reissued.refreshToken()).isNotBlank();

    assertThat(refreshStore.exists(user.getId(), rotatedParsed.jti())).isTrue();
    assertThat(refreshStore.exists(user.getId(), otherSession.jti())).isTrue();
  }

  @Test
  void aLoggedOutSessionIsNotRevivedByTheTokenItRotatedFrom() {
    IssuedTokens login =
        ((AuthService.TokenLoginResult.Tokens)
                authService.loginWithOAuth("out@example.com", "google", "g-out"))
            .issued();
    IssuedTokens rotated = authService.refresh(login.refreshToken(), SessionChannel.WEB);

    authService.logout(rotated.refreshToken());

    assertThatThrownBy(() -> authService.refresh(login.refreshToken(), SessionChannel.WEB))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN));
  }

  @Test
  void logoutDeletesRefresh() {
    UserEntity user = userRepository.save(new UserEntity("u@example.com", "google", "g-u"));
    RefreshToken refresh = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl(SessionChannel.WEB));

    authService.logout(user.getId(), refresh.token());

    assertThat(refreshStore.exists(user.getId(), refresh.jti())).isFalse();
  }

  @Test
  void anAppRotationKeepsTheSessionForAYearAndCarriesNoWebMark() {
    UserEntity user = userRepository.save(new UserEntity("app@example.com", "apple", "a-app"));
    RefreshToken current = jwt.createRefreshToken(user.getId(), null, SessionChannel.MOBILE);
    refreshStore.save(user.getId(), current.jti(), jwt.refreshTtl(SessionChannel.MOBILE));

    IssuedTokens rotated = authService.refresh(current.token(), SessionChannel.MOBILE);

    assertLasts(rotated.refreshToken(), Duration.ofDays(365));
    assertThat(jwt.parseRefreshToken(rotated.refreshToken()).webOnly()).isFalse();
  }

  @Test
  void aWebRotationStaysFourteenDaysAndIsMarkedWeb() {
    UserEntity user = userRepository.save(new UserEntity("web@example.com", "google", "g-web"));
    RefreshToken current = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), current.jti(), jwt.refreshTtl(SessionChannel.WEB));

    IssuedTokens rotated = authService.refresh(current.token(), SessionChannel.WEB);

    assertLasts(rotated.refreshToken(), Duration.ofDays(14));
    assertThat(jwt.parseRefreshToken(rotated.refreshToken()).webOnly()).isTrue();
  }

  @Test
  void aFourteenDaySessionFromBeforeThisBecomesAYearOnItsFirstAppRefresh() {
    UserEntity user = userRepository.save(new UserEntity("old@example.com", "apple", "a-old"));
    RefreshToken unmarked =
        jwt.createRefreshToken(user.getId(), "old-session", SessionChannel.MOBILE);
    refreshStore.save(user.getId(), unmarked.jti(), Duration.ofDays(14));

    IssuedTokens rotated = authService.refresh(unmarked.token(), SessionChannel.MOBILE);

    assertLasts(rotated.refreshToken(), Duration.ofDays(365));
    assertThat(jwt.parseRefreshToken(rotated.refreshToken()).sessionId()).isEqualTo("old-session");
  }

  @Test
  void aWebTokenSentToTheAppRefreshIsTurnedAwayAndStaysUsableOnTheWeb() {
    UserEntity user = userRepository.save(new UserEntity("mix@example.com", "google", "g-mix"));
    RefreshToken web = jwt.createRefreshToken(user.getId(), null, SessionChannel.WEB);
    refreshStore.save(user.getId(), web.jti(), jwt.refreshTtl(SessionChannel.WEB));

    assertThatThrownBy(() -> authService.refresh(web.token(), SessionChannel.MOBILE))
        .isInstanceOf(UserException.class);

    assertThat(refreshStore.exists(user.getId(), web.jti())).isTrue();
    assertLasts(
        authService.refresh(web.token(), SessionChannel.WEB).refreshToken(), Duration.ofDays(14));
  }

  @ParameterizedTest
  @EnumSource(SessionChannel.class)
  void aReplayWithinTheRotationGraceIsReissuedForTheSameChannel(SessionChannel channel) {
    UserEntity user =
        userRepository.save(new UserEntity(channel + "@grace.test", "google", "g-" + channel));
    RefreshToken initial = jwt.createRefreshToken(user.getId(), null, channel);
    refreshStore.save(user.getId(), initial.jti(), jwt.refreshTtl(channel));

    IssuedTokens rotated = authService.refresh(initial.token(), channel);
    IssuedTokens replayed = authService.refresh(initial.token(), channel);

    assertThat(replayed.refreshToken()).isNotEqualTo(rotated.refreshToken());
    assertLasts(replayed.refreshToken(), jwt.refreshTtl(channel));
    assertThat(
            refreshStore.exists(user.getId(), jwt.parseRefreshToken(rotated.refreshToken()).jti()))
        .isTrue();
  }

  private void assertLasts(String refreshToken, Duration expected) {
    ParsedRefresh parsed = jwt.parseRefreshToken(refreshToken);
    Long redisSeconds =
        redis.getExpire("refresh:" + parsed.userId() + ":" + parsed.jti(), TimeUnit.SECONDS);
    assertThat(redisSeconds).isBetween(expected.toSeconds() - 60, expected.toSeconds());
    long exp =
        json.readTree(Base64.getUrlDecoder().decode(refreshToken.split("\\.")[1]))
            .path("exp")
            .asLong();
    assertThat(exp - Instant.now().getEpochSecond())
        .isBetween(expected.toSeconds() - 60, expected.toSeconds());
  }
}
