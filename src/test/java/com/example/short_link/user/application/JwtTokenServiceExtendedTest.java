package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.dto.ParsedAccess;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenServiceExtendedTest {

  private JwtTokenService freshService() throws Exception {
    return new JwtTokenService(
        new JwtProperties(
            "", "", Duration.ofMinutes(5), Duration.ofDays(7), Duration.ofSeconds(10)));
  }

  @Test
  void createAccessTokenWithRole() throws Exception {
    JwtTokenService svc = freshService();
    String token = svc.createAccessToken(42L, "ADMIN");
    ParsedAccess parsed = svc.parseAccessTokenDetailed(token);
    assertThat(parsed.userId()).isEqualTo(42L);
    assertThat(parsed.role()).isEqualTo("ADMIN");
  }

  @Test
  void parseAccessTokenDetailedDefaultsRoleWhenMissing() throws Exception {
    JwtTokenService svc = freshService();
    String token = svc.createAccessToken(42L, "USER");
    assertThat(svc.parseAccessTokenDetailed(token).role()).isEqualTo("USER");
  }

  @Test
  void parseAccessTokenDetailedRejectsRefreshToken() throws Exception {
    JwtTokenService svc = freshService();
    String refresh = svc.createRefreshToken(42L).token();
    assertThatThrownBy(() -> svc.parseAccessTokenDetailed(refresh))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.INVALID_TOKEN_TYPE);
  }

  @Test
  void twoFactorChallengeRoundTrip() throws Exception {
    JwtTokenService svc = freshService();
    String token = svc.createTwoFactorChallengeToken(42L);
    assertThat(svc.parseTwoFactorChallengeToken(token)).isEqualTo(42L);
  }

  @Test
  void twoFactorChallengeRejectsAccessToken() throws Exception {
    JwtTokenService svc = freshService();
    String access = svc.createAccessToken(42L, "USER");
    assertThatThrownBy(() -> svc.parseTwoFactorChallengeToken(access))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.INVALID_TOKEN_TYPE);
  }

  @Test
  void refreshTtlAccessor() throws Exception {
    JwtTokenService svc =
        new JwtTokenService(
            new JwtProperties(
                "", "", Duration.ofMinutes(1), Duration.ofDays(30), Duration.ofSeconds(10)));
    assertThat(svc.refreshTtl()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  void accessTtlAccessor() throws Exception {
    JwtTokenService svc =
        new JwtTokenService(
            new JwtProperties(
                "", "", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(10)));
    assertThat(svc.accessTtl()).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void serviceLoadsConfiguredKeysWhenProvided() throws Exception {
    KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    String priv =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    String pub =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    JwtTokenService svc =
        new JwtTokenService(
            new JwtProperties(
                priv, pub, Duration.ofMinutes(5), Duration.ofDays(7), Duration.ofSeconds(10)));
    String token = svc.createAccessToken(42L, "USER");
    assertThat(svc.parseAccessToken(token)).isEqualTo(42L);
  }

  @Test
  void invalidPrivateKeyThrows() {
    assertThatThrownBy(
            () ->
                new JwtTokenService(
                    new JwtProperties(
                        "-----BEGIN PRIVATE KEY-----\nXXX\n-----END PRIVATE KEY-----",
                        "-----BEGIN PUBLIC KEY-----\nXXX\n-----END PUBLIC KEY-----",
                        Duration.ofMinutes(5),
                        Duration.ofDays(7),
                        Duration.ofSeconds(10))))
        .isInstanceOf(GeneralSecurityException.class);
  }
}
