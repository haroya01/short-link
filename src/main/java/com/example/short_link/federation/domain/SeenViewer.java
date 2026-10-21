package com.example.short_link.federation.domain;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.regex.Pattern;

public record SeenViewer(int kind, UUID key) {

  public static final int MEMBER = 1;
  public static final int DEVICE = 2;

  private static final Pattern UUID_V4 =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

  public static SeenViewer member(long userId) {
    return new SeenViewer(MEMBER, new UUID(0L, userId));
  }

  public static SeenViewer of(Long userId, String device) {
    if (userId != null) {
      return member(userId);
    }
    if (device == null || !UUID_V4.matcher(device).matches()) {
      return null;
    }
    return new SeenViewer(DEVICE, UUID.fromString(device));
  }

  public byte[] keyBytes() {
    return ByteBuffer.allocate(16)
        .putLong(key.getMostSignificantBits())
        .putLong(key.getLeastSignificantBits())
        .array();
  }
}
