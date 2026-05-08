package com.example.short_link.link.application;

import java.util.Locale;
import java.util.Set;

/**
 * Short codes that must not be claimable as custom codes (and not generated either) because they
 * collide with frontend routes, API paths, or operational endpoints. Comparison is case-insensitive
 * — short codes are case-sensitive in storage, but route collisions don't care about case.
 */
public final class ReservedShortCodes {

  private static final Set<String> RESERVED =
      Set.of(
          // frontend routes
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
          // api / ops
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
          "manifest",
          "graphql",
          // marketing / generic
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
          // operational reserved
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
