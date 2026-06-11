package com.example.short_link.user.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Marks an OAuth dance as app-initiated. {@code /api/v1/auth/mobile/start} sets the flag before
 * forwarding to the provider; the success/failure handlers consume it to pick the custom-scheme
 * redirect over the web callback. Rides the same HTTP session Spring Security already uses to keep
 * the OAuth authorization request across the redirect round-trip, so it needs no extra state.
 */
public final class MobileLoginFlag {

  private static final String ATTR = "short-link.mobile-oauth-login";

  private MobileLoginFlag() {}

  public static void mark(HttpServletRequest req) {
    req.getSession(true).setAttribute(ATTR, Boolean.TRUE);
  }

  /** True if the dance was app-initiated; clears the flag so it can't leak into a later login. */
  public static boolean consume(HttpServletRequest req) {
    HttpSession session = req.getSession(false);
    if (session == null) return false;
    Object flag = session.getAttribute(ATTR);
    if (flag == null) return false;
    session.removeAttribute(ATTR);
    return true;
  }
}
