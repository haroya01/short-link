package com.example.short_link.user.application.write;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.SessionChannel;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AuthServiceDecisionsTest {

  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
  private static final String SUBJECT = "apple-subject";
  private static final String EMAIL = "mina@example.com";

  private final UserRepository users = mock(UserRepository.class);
  private final JwtTokenService jwt = mock(JwtTokenService.class);
  private final RefreshTokenStore refreshStore = mock(RefreshTokenStore.class);
  private final TwoFactorService twoFactor = mock(TwoFactorService.class);
  private final DeviceTokenRepository deviceTokens = mock(DeviceTokenRepository.class);
  private final AuthService auth =
      new AuthService(
          users,
          jwt,
          refreshStore,
          mock(MobileExchangeCodeStore.class),
          twoFactor,
          new JwtProperties(
              null,
              null,
              Duration.ofMinutes(15),
              Duration.ofDays(14),
              Duration.ofSeconds(30),
              Duration.ofDays(365)),
          deviceTokens,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void tokens() {
    when(jwt.createAccessToken(any(), anyString(), anyString())).thenReturn("access");
    when(jwt.createRefreshToken(any(), anyString(), any()))
        .thenReturn(new RefreshToken("refresh", "jti"));
    when(jwt.refreshTtl(SessionChannel.WEB)).thenReturn(Duration.ofDays(14));
    when(jwt.refreshTtl(SessionChannel.MOBILE)).thenReturn(Duration.ofDays(365));
    when(users.findByOauthProviderAndOauthId("apple", SUBJECT)).thenReturn(Optional.empty());
    when(users.findByEmail(any())).thenReturn(Optional.empty());
    when(users.save(any())).thenAnswer(saved -> withId(saved.getArgument(0), 99L));
  }

  private static UserEntity member(Long id, String provider) {
    return withId(new UserEntity(EMAIL, provider, provider + "-id"), id);
  }

  @Test
  void aKnownAppleSubjectSignsInThatAccountWithoutLookingAtTheEmail() {
    UserEntity known = member(7L, "apple");
    when(users.findByOauthProviderAndOauthId("apple", SUBJECT)).thenReturn(Optional.of(known));

    assertThat(auth.loginWithApple(SUBJECT, EMAIL, SessionChannel.WEB))
        .isInstanceOf(AuthService.TokenLoginResult.Tokens.class);

    verify(users, never()).findByEmail(any());
    verify(users, never()).save(any());
    verify(jwt).createRefreshToken(eq(7L), anyString(), eq(SessionChannel.WEB));
  }

  @Test
  void anUnknownSubjectWithTheEmailOfAnAccountLinksToItAndKeepsItsOriginalSignIn() {
    UserEntity google = member(8L, "google");
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(google));

    auth.loginWithApple(SUBJECT, EMAIL, SessionChannel.MOBILE);

    verify(users, never()).save(any());
    assertThat(google.getOauthProvider()).isEqualTo("google");
    verify(jwt).createRefreshToken(eq(8L), anyString(), eq(SessionChannel.MOBILE));
  }

  @Test
  void anUnknownSubjectWithANewEmailCreatesAnAppleAccountThatRecordsItsConsent() {
    auth.loginWithApple(SUBJECT, EMAIL, SessionChannel.WEB);

    ArgumentCaptor<UserEntity> created = ArgumentCaptor.forClass(UserEntity.class);
    verify(users).save(created.capture());
    assertThat(created.getValue().getEmail()).isEqualTo(EMAIL);
    assertThat(created.getValue().getOauthProvider()).isEqualTo("apple");
    assertThat(created.getValue().getOauthId()).isEqualTo(SUBJECT);
    assertThat(created.getValue().getTermsVersion()).isNotBlank();
    assertThat(created.getValue().getTermsAgreedAt()).isNotNull();
  }

  @ParameterizedTest(name = "email=[{0}]")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void anUnknownSubjectWithoutAnEmailNeitherLinksNorCreates(String email) {
    assertThatThrownBy(() -> auth.loginWithApple(SUBJECT, email, SessionChannel.WEB))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.APPLE_EMAIL_REQUIRED));
    verify(users, never()).findByEmail(any());
    verify(users, never()).save(any());
  }

  @Test
  void aDeletedAccountFoundEitherWayIsRestoredOnSignIn() {
    UserEntity gone = member(9L, "google");
    gone.softDelete();
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(gone));

    auth.loginWithApple(SUBJECT, EMAIL, SessionChannel.WEB);

    assertThat(gone.isDeleted()).isFalse();
  }

  @Test
  void anAccountWithTwoFactorGetsAChallengeInsteadOfASession() {
    UserEntity guarded = member(10L, "apple");
    when(users.findByOauthProviderAndOauthId("apple", SUBJECT)).thenReturn(Optional.of(guarded));
    when(twoFactor.isEnabled(10L)).thenReturn(true);
    when(jwt.createTwoFactorChallengeToken(10L)).thenReturn("challenge");

    assertThat(auth.loginWithApple(SUBJECT, EMAIL, SessionChannel.WEB))
        .isEqualTo(new AuthService.TokenLoginResult.TwoFactorRequired("challenge"));
    verify(jwt, never()).createRefreshToken(any(), anyString(), any());
  }

  @Test
  void anOAuthSignUpRecordsConsentToo() {
    when(users.findByOauthProviderAndOauthId("google", "g-1")).thenReturn(Optional.empty());

    auth.loginWithOAuth(EMAIL, "google", "g-1");

    ArgumentCaptor<UserEntity> created = ArgumentCaptor.forClass(UserEntity.class);
    verify(users).save(created.capture());
    assertThat(created.getValue().getTermsAgreedAt()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(SessionChannel.class)
  void aRefreshKeepsTheSessionsPushDevicesAliveForThatChannelsLifetime(SessionChannel channel) {
    when(jwt.parseRefreshToken("token"))
        .thenReturn(new ParsedRefresh(11L, "old-jti", "session-1", false));
    when(refreshStore.consume(11L, "old-jti", Duration.ofSeconds(30))).thenReturn(true);
    when(users.findById(11L)).thenReturn(Optional.of(member(11L, "google")));

    auth.refresh("token", channel);

    verify(deviceTokens)
        .extendSession(
            11L,
            "session-1",
            NOW.plus(
                channel == SessionChannel.MOBILE ? Duration.ofDays(365) : Duration.ofDays(14)));
  }

  @ParameterizedTest(name = "recovery={0} accepted={1}")
  @CsvSource(
      textBlock =
          """
          false, true
          true,  true
          false, false
          true,  false
          """)
  void aSecondStepChecksTheCodeWithTheVerifierItNamesAndOnlyThenOpensASession(
      boolean recovery, boolean accepted) {
    when(jwt.parseTwoFactorChallengeToken("challenge")).thenReturn(12L);
    when(users.findById(12L)).thenReturn(Optional.of(member(12L, "google")));
    when(twoFactor.verify(12L, "123456")).thenReturn(!recovery && accepted);
    when(twoFactor.verifyRecovery(12L, "123456")).thenReturn(recovery && accepted);

    if (accepted) {
      assertThat(auth.completeTwoFactor("challenge", "123456", recovery, SessionChannel.MOBILE))
          .isEqualTo(new IssuedTokens("access", "refresh"));
      verify(jwt).createRefreshToken(eq(12L), anyString(), eq(SessionChannel.MOBILE));
    } else {
      assertThatThrownBy(
              () -> auth.completeTwoFactor("challenge", "123456", recovery, SessionChannel.MOBILE))
          .isInstanceOfSatisfying(
              UserException.class,
              e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.INVALID_TOTP));
      verify(jwt, never()).createRefreshToken(any(), anyString(), any());
    }
    if (recovery) {
      verify(twoFactor, never()).verify(any(), anyString());
    } else {
      verify(twoFactor, never()).verifyRecovery(any(), anyString());
    }
  }

  @Test
  void anOAuthSignInToATwoFactorAccountGetsAChallengeOnTheWebAndInTheApp() {
    UserEntity guarded = member(13L, "google");
    when(users.findByOauthProviderAndOauthId("google", "g-13")).thenReturn(Optional.of(guarded));
    when(twoFactor.isEnabled(13L)).thenReturn(true);
    when(jwt.createTwoFactorChallengeToken(13L)).thenReturn("challenge");

    assertThat(auth.loginWithOAuth(EMAIL, "google", "g-13"))
        .isEqualTo(new AuthService.TokenLoginResult.TwoFactorRequired("challenge"));
    assertThat(auth.loginWithOAuthMobile(EMAIL, "google", "g-13"))
        .isEqualTo(new AuthService.MobileLoginResult.TwoFactorRequired("challenge"));
  }

  @Test
  void aRefreshForAnAccountThatNoLongerExistsIsRefused() {
    when(jwt.parseRefreshToken("token"))
        .thenReturn(new ParsedRefresh(14L, "jti-14", "session-14", false));
    when(refreshStore.consume(14L, "jti-14", Duration.ofSeconds(30))).thenReturn(true);
    when(users.findById(14L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> auth.refresh("token", SessionChannel.WEB))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }

  @Test
  void aRightCodeForAnAccountRemovedMeanwhileOpensNothing() {
    when(jwt.parseTwoFactorChallengeToken("challenge")).thenReturn(15L);
    when(twoFactor.verify(15L, "123456")).thenReturn(true);
    when(users.findById(15L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> auth.completeTwoFactor("challenge", "123456", false, SessionChannel.WEB))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
  }
}
