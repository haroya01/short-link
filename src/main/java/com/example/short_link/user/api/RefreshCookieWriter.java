package com.example.short_link.user.api;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieWriter {

  private static final String COOKIE_NAME = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/auth";

  private final boolean secure;
  private final Duration maxAge;

  public RefreshCookieWriter(
      @Value("${short-link.cookie.secure}") boolean secure,
      @Value("${short-link.jwt.refresh-ttl}") Duration maxAge) {
    this.secure = secure;
    this.maxAge = maxAge;
  }

  public void set(HttpServletResponse res, String token) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public void clear(HttpServletResponse res) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(0)
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
