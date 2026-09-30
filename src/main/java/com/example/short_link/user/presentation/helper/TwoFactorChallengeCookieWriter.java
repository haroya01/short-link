package com.example.short_link.user.presentation.helper;

import com.example.short_link.user.application.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class TwoFactorChallengeCookieWriter {

  public static final String COOKIE_NAME = "twofa_challenge";
  private static final String COOKIE_PATH = "/api/v1/auth/2fa";

  private final AuthCookieAttributes attributes;

  public TwoFactorChallengeCookieWriter(
      @Value("${short-link.cookie.secure}") boolean secure,
      @Value("${short-link.cookie.domain:}") String domain,
      @Value("${short-link.cookie.same-site:Strict}") String sameSite) {
    this.attributes = new AuthCookieAttributes(secure, domain, sameSite);
  }

  public void set(HttpServletResponse res, String challengeToken) {
    res.addHeader(
        HttpHeaders.SET_COOKIE,
        attributes
            .httpOnly(COOKIE_NAME, challengeToken, COOKIE_PATH, JwtTokenService.CHALLENGE_TTL)
            .toString());
  }

  public void clear(HttpServletResponse res) {
    res.addHeader(
        HttpHeaders.SET_COOKIE,
        attributes.httpOnly(COOKIE_NAME, "", COOKIE_PATH, Duration.ZERO).toString());
  }
}
