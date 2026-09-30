package com.example.short_link.user.presentation.helper;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

record AuthCookieAttributes(boolean secure, String domain, String sameSite) {

  ResponseCookie httpOnly(String name, String value, String path, Duration ttl) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(path)
            .maxAge(ttl);
    if (StringUtils.hasText(domain)) {
      builder.domain(domain);
    }
    return builder.build();
  }
}
