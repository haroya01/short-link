package com.example.short_link.federation.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;

public record SeenUri(long high, long low) {

  public static final int MAX_LENGTH = 2048;

  public static SeenUri of(String uri) {
    ByteBuffer digest = ByteBuffer.wrap(sha256(uri.getBytes(StandardCharsets.UTF_8)));
    return new SeenUri(digest.getLong(), digest.getLong());
  }

  public static Optional<SeenUri> sent(String uri) {
    if (uri == null || uri.isEmpty() || uri.length() > MAX_LENGTH) {
      return Optional.empty();
    }
    try {
      URI parsed = new URI(uri);
      String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
      if (!(scheme.equals("https") || scheme.equals("http")) || parsed.getHost() == null) {
        return Optional.empty();
      }
    } catch (URISyntaxException malformed) {
      return Optional.empty();
    }
    return Optional.of(of(uri));
  }

  public byte[] bytes() {
    return ByteBuffer.allocate(16).putLong(high).putLong(low).array();
  }

  public static SeenUri fromBytes(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new SeenUri(buffer.getLong(), buffer.getLong());
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }
}
