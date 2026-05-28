package com.example.short_link.user.presentation.helper;

import com.example.short_link.user.application.properties.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefreshCookieWriter {

  private static final String COOKIE_NAME = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/auth";

  private final boolean secure;
  private final String domain;
  private final String sameSite;
  private final Duration maxAge;

  public RefreshCookieWriter(
      @Value("${short-link.cookie.secure}") boolean secure,
      @Value("${short-link.cookie.domain:}") String domain,
      @Value("${short-link.cookie.same-site:Strict}") String sameSite,
      JwtProperties jwt) {
    this.secure = secure;
    this.domain = domain;
    this.sameSite = sameSite;
    this.maxAge = jwt.refreshTtl();
  }

  public void set(HttpServletResponse res, String token) {
    res.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge).toString());
  }

  public void clear(HttpServletResponse res) {
    res.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
  }

  private ResponseCookie build(String value, Duration ttl) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(COOKIE_PATH)
            .maxAge(ttl);
    if (StringUtils.hasText(domain)) {
      builder.domain(domain);
    }
    return builder.build();
  }
}
