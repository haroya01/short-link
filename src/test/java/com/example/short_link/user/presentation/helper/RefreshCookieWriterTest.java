package com.example.short_link.user.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.application.properties.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshCookieWriterTest {

  private JwtProperties jwt() {
    return new JwtProperties(null, null, Duration.ofMinutes(15), Duration.ofDays(14));
  }

  @Test
  void crossSubdomainCookieCarriesDomainAndSameSite() {
    RefreshCookieWriter writer = new RefreshCookieWriter(true, ".kurl.me", "Lax", jwt());
    MockHttpServletResponse res = new MockHttpServletResponse();

    writer.set(res, "refresh-jwt");

    String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).contains("refresh_token=refresh-jwt");
    assertThat(cookie).contains("Domain=.kurl.me");
    assertThat(cookie).contains("SameSite=Lax");
    assertThat(cookie).contains("Secure");
    assertThat(cookie).contains("HttpOnly");
    assertThat(cookie).contains("Path=/api/v1/auth");
  }

  @Test
  void hostOnlyCookieWhenDomainBlank() {
    RefreshCookieWriter writer = new RefreshCookieWriter(false, "", "Strict", jwt());
    MockHttpServletResponse res = new MockHttpServletResponse();

    writer.set(res, "refresh-jwt");

    String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).doesNotContain("Domain=");
    assertThat(cookie).contains("SameSite=Strict");
  }

  @Test
  void clearReusesDomainAndExpiresImmediately() {
    RefreshCookieWriter writer = new RefreshCookieWriter(true, ".kurl.me", "Lax", jwt());
    MockHttpServletResponse res = new MockHttpServletResponse();

    writer.clear(res);

    String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).contains("Domain=.kurl.me");
    assertThat(cookie).contains("Max-Age=0");
  }
}
