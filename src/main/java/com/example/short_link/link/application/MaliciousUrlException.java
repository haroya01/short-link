package com.example.short_link.link.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MaliciousUrlException extends RuntimeException {

  public MaliciousUrlException(String url) {
    super("malicious url rejected (sha256_prefix=" + hashPrefix(url) + ")");
  }

  private static String hashPrefix(String url) {
    String input = url == null ? "" : url;
    byte[] digest = sha256().digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder(16);
    for (int i = 0; i < 8; i++) {
      hex.append(String.format("%02x", digest[i]));
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
