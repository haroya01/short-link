package com.example.short_link.user.presentation.helper;

import com.example.short_link.common.security.OperatorMarkerStore;
import com.example.short_link.user.application.properties.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

@Slf4j
@Component
public class OperatorMarkerCookie {

  private static final String COOKIE_PATH = "/";
  private static final String SAME_SITE = "Lax";

  private final OperatorMarkerStore markers;
  private final AuthCookieAttributes attributes;
  private final Duration ttl;

  public OperatorMarkerCookie(
      OperatorMarkerStore markers,
      @Value("${short-link.cookie.secure}") boolean secure,
      @Value("${short-link.cookie.domain:}") String domain,
      JwtProperties jwt) {
    this.markers = markers;
    this.attributes = new AuthCookieAttributes(secure, domain, SAME_SITE);
    this.ttl = jwt.refreshTtl();
  }

  public void onLogin(HttpServletRequest req, HttpServletResponse res, boolean admin) {
    String carried = carried(req);
    if (!admin) {
      drop(res, carried);
      return;
    }
    quietly(
        () -> {
          markers.revoke(carried);
          write(res, markers.issue(ttl), ttl);
        });
  }

  public void onRefresh(HttpServletRequest req, HttpServletResponse res, boolean admin) {
    String carried = carried(req);
    if (!admin) {
      drop(res, carried);
      return;
    }
    quietly(() -> write(res, markers.extend(carried, ttl) ? carried : markers.issue(ttl), ttl));
  }

  public void onLogout(HttpServletRequest req, HttpServletResponse res) {
    drop(res, carried(req));
  }

  private void drop(HttpServletResponse res, String carried) {
    if (carried == null) {
      return;
    }
    write(res, "", Duration.ZERO);
    quietly(() -> markers.revoke(carried));
  }

  private void write(HttpServletResponse res, String value, Duration maxAge) {
    res.addHeader(
        HttpHeaders.SET_COOKIE,
        attributes
            .httpOnly(OperatorMarkerStore.COOKIE_NAME, value, COOKIE_PATH, maxAge)
            .toString());
  }

  private static String carried(HttpServletRequest req) {
    Cookie cookie = WebUtils.getCookie(req, OperatorMarkerStore.COOKIE_NAME);
    return cookie == null ? null : cookie.getValue();
  }

  private static void quietly(Runnable markerWork) {
    try {
      markerWork.run();
    } catch (DataAccessException e) {
      log.warn("operator marker left as it was: {}", e.toString());
    }
  }
}
