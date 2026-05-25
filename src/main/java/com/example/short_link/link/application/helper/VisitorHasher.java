package com.example.short_link.link.application.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class VisitorHasher {

  private VisitorHasher() {}

  public static String hash(Long linkId, String clientIp, String userAgent) {
    String input =
        (linkId == null ? "" : linkId.toString())
            + "|"
            + (clientIp == null ? "" : clientIp)
            + "|"
            + (userAgent == null ? "" : userAgent);
    byte[] digest = sha256().digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(64);
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
