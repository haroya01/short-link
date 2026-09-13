package com.example.short_link.link.application.helper;

import java.util.Locale;
import java.util.Set;

/**
 * Reserves route and operational endpoint names case-insensitively, even though stored short codes
 * are case-sensitive.
 */
public final class ReservedShortCodes {

  private static final Set<String> RESERVED =
      Set.of(
          "login",
          "logout",
          "signup",
          "register",
          "dashboard",
          "admin",
          "stats",
          "auth",
          "callback",
          "settings",
          "account",
          "me",
          "profile",
          "demo",
          "showcase",
          "monitoring",
          "learn",
          "campaigns",
          "api",
          "v1",
          "v2",
          "oauth2",
          "actuator",
          "health",
          "metrics",
          "swagger",
          "swagger-ui",
          "openapi",
          "docs",
          "robots",
          "sitemap",
          "favicon",
          "icon",
          "manifest",
          "graphql",
          "home",
          "index",
          "www",
          "mail",
          "about",
          "pricing",
          "terms",
          "privacy",
          "help",
          "contact",
          "support",
          "blog",
          "press",
          "kurl",
          "static",
          "assets",
          "public",
          "_next",
          "_vercel");

  private ReservedShortCodes() {}

  public static boolean isReserved(String code) {
    if (code == null) return false;
    return RESERVED.contains(code.toLowerCase(Locale.ROOT));
  }
}
