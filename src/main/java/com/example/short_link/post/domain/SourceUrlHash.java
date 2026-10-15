package com.example.short_link.post.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SourceUrlHash {

  private SourceUrlHash() {}

  public static String of(String url) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
