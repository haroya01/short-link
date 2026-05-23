package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private JwtTokenService service;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new JwtTokenService(new JwtProperties("", "", Duration.ofMinutes(15), Duration.ofDays(14)));
  }

  @Test
  void parseAccessTokenReturnsUserId() {
    String token = service.createAccessToken(42L);

    assertThat(service.parseAccessToken(token)).isEqualTo(42L);
  }

  @Test
  void parseAccessTokenRejectsRefreshToken() {
    String refresh = service.createRefreshToken(42L).token();

    assertThatThrownBy(() -> service.parseAccessToken(refresh))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseRefreshTokenReturnsUserIdAndJti() {
    RefreshToken refresh = service.createRefreshToken(42L);

    ParsedRefresh parsed = service.parseRefreshToken(refresh.token());

    assertThat(parsed.userId()).isEqualTo(42L);
    assertThat(parsed.jti()).isEqualTo(refresh.jti());
  }

  @Test
  void parseRefreshTokenRejectsAccessToken() {
    String access = service.createAccessToken(42L);

    assertThatThrownBy(() -> service.parseRefreshToken(access))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
