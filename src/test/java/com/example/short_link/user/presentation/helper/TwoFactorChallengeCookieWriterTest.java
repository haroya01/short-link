package com.example.short_link.user.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class TwoFactorChallengeCookieWriterTest {

  @Test
  void challengeLivesOnlyAsLongAsTheTokenAndOnlyReachesTheVerifyPath() {
    TwoFactorChallengeCookieWriter writer =
        new TwoFactorChallengeCookieWriter(true, ".kurl.me", "Lax");
    MockHttpServletResponse res = new MockHttpServletResponse();

    writer.set(res, "challenge-jwt");

    String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).contains("twofa_challenge=challenge-jwt");
    assertThat(cookie).contains("Max-Age=300");
    assertThat(cookie).contains("Path=/api/v1/auth/2fa");
    assertThat(cookie).contains("HttpOnly");
    assertThat(cookie).contains("Secure");
    assertThat(cookie).contains("Domain=.kurl.me");
    assertThat(cookie).contains("SameSite=Lax");
  }

  @Test
  void clearExpiresTheSameCookie() {
    TwoFactorChallengeCookieWriter writer =
        new TwoFactorChallengeCookieWriter(true, ".kurl.me", "Lax");
    MockHttpServletResponse res = new MockHttpServletResponse();

    writer.clear(res);

    String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).contains("twofa_challenge=");
    assertThat(cookie).contains("Max-Age=0");
    assertThat(cookie).contains("Path=/api/v1/auth/2fa");
    assertThat(cookie).contains("Domain=.kurl.me");
  }
}
