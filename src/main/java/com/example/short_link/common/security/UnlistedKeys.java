package com.example.short_link.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class UnlistedKeys {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UnlistedKeys() {}

  public static String next() {
    byte[] key = new byte[24];
    RANDOM.nextBytes(key);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
  }

  public static boolean matches(String expected, String given) {
    return given != null
        && expected != null
        && MessageDigest.isEqual(
            given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }
}
