package com.example.short_link.profile.application;

import com.example.short_link.profile.application.Socials.Social;
import java.util.List;

public record PublicProfile(
    String username,
    String bio,
    String theme,
    String avatarUrl,
    String bannerUrl,
    List<Social> socials,
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

    public static ProfileEntry embed(Long id, String url) {
      return new ProfileEntry("EMBED", id, null, null, null, null, null, null, null, url);
    }

    /** Content is the EMAIL_FORM JSON config (title / placeholder / successMessage). */
    public static ProfileEntry emailForm(Long id, String config) {
      return new ProfileEntry("EMAIL_FORM", id, null, null, null, null, null, null, null, config);
    }

    /** Content is the CONTACT_CARD JSON (name + optional title/company/email/phone/etc.). */
    public static ProfileEntry contactCard(Long id, String config) {
      return new ProfileEntry("CONTACT_CARD", id, null, null, null, null, null, null, null, config);
    }

    /** Content is the GALLERY JSON ({@code {"images":["url",...]}}). */
    public static ProfileEntry gallery(Long id, String config) {
      return new ProfileEntry("GALLERY", id, null, null, null, null, null, null, null, config);
    }

    /** Content is the PRODUCT_CARD JSON ({@code {title?, items: [...]}}). */
    public static ProfileEntry productCard(Long id, String config) {
      return new ProfileEntry("PRODUCT_CARD", id, null, null, null, null, null, null, null, config);
    }
  }
}
