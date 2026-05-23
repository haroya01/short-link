package com.example.short_link.profile.application;

import java.util.Set;

/** Usernames the public profile route would collide with or that should never be claimable. */
public final class ReservedUsernames {

  public static final Set<String> ALL =
      Set.of(
          "admin",
          "api",
          "auth",
          "billing",
          "dashboard",
          "data",
          "docs",
          "explore",
          "help",
          "home",
          "kurl",
          "login",
          "logout",
          "me",
          "oauth2",
          "pricing",
          "privacy",
          "public",
          "robots",
          "settings",
          "signup",
          "sitemap",
          "static",
          "stats",
          "status",
          "support",
          "swagger",
          "terms",
          "tos",
          "u",
          "user",
          "users",
          "v1",
          "www");

  private ReservedUsernames() {}
}
