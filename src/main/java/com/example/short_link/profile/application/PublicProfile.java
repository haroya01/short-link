package com.example.short_link.profile.application;

import java.util.List;

public record PublicProfile(
    String username,
    String bio,
    String theme,
    String avatarUrl,
    String bannerUrl,
    List<ProfileEntry> entries) {

  /**
   * Single shape for the rendered public profile feed. The {@code kind} discriminator drives
   * frontend rendering; fields not relevant to a given kind are null. Sparse but JSON-flat —
   * Jackson handles this without sealed-class plumbing.
   */
  public record ProfileEntry(
      String kind,
      Long id,
      String shortCode,
      String shortUrl,
      String originalUrl,
      String ogTitle,
      String ogImage,
      Long clickCount,
      Boolean highlighted,
      String content) {

    public static ProfileEntry link(
        String shortCode,
        String shortUrl,
        String originalUrl,
        String ogTitle,
        String ogImage,
        long clickCount,
        boolean highlighted) {
      return new ProfileEntry(
          "LINK",
          null,
          shortCode,
          shortUrl,
          originalUrl,
          ogTitle,
          ogImage,
          clickCount,
          highlighted,
          null);
    }

    public static ProfileEntry text(Long id, String content) {
      return new ProfileEntry("TEXT", id, null, null, null, null, null, null, null, content);
    }

    public static ProfileEntry divider(Long id) {
      return new ProfileEntry("DIVIDER", id, null, null, null, null, null, null, null, null);
    }

    public static ProfileEntry image(Long id, String url) {
      return new ProfileEntry("IMAGE", id, null, null, null, null, null, null, null, url);
    }
  }
}
