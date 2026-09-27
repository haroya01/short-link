package com.example.short_link.user.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class MobileLoginFlag {

  private static final String ATTR = "short-link.mobile-oauth-login";

  private MobileLoginFlag() {}

  public static void mark(HttpServletRequest req) {
    req.getSession(true).setAttribute(ATTR, Boolean.TRUE);
  }

  public static boolean consume(HttpServletRequest req) {
    HttpSession session = req.getSession(false);
    if (session == null) return false;
    Object flag = session.getAttribute(ATTR);
    if (flag == null) return false;
    session.removeAttribute(ATTR);
    return true;
  }
}
