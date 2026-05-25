package com.example.short_link.link.application.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 prefix of a user-supplied URL — used in malicious-URL responses / logs so we never echo
 * the raw URL back (avoids reflecting phishing strings into logs and clients while keeping enough
 * fingerprint to correlate with upstream Safe Browsing telemetry).
 */
public final class LinkUrlHasher {

  private LinkUrlHasher() {}

  public static String sha256Prefix(String url) {
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
