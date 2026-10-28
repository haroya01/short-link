package com.example.short_link.common.user;

import java.util.Locale;

public final class Usernames {

  public static final String GRAMMAR = "[a-z0-9][a-z0-9_]{2,15}";

  private Usernames() {}

  public static String canonical(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }
}
