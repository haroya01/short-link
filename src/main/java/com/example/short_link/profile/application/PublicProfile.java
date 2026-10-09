package com.example.short_link.profile.application;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.profile.application.Socials.Social;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PublicProfile(
    String username,
    String bio,
    String theme,
    String avatarUrl,
    String bannerUrl,
    List<Social> socials,
    List<ProfileEntry> entries,
    long publishedPostCount,
    boolean hideFollowerCount) {

  public PublicProfile withEntries(List<ProfileEntry> visibleEntries) {
    return new PublicProfile(
        username,
        bio,
        theme,
        avatarUrl,
        bannerUrl,
        socials,
        visibleEntries,
        publishedPostCount,
        hideFollowerCount);
  }

  // Clients send visitors of a protected link to shortUrl, which asks for the password.
  public record ProfileEntry(
      String kind,
      Long id,
      ShortCode shortCode,
      String shortUrl,
      String originalUrl,
      String ogTitle,
      String ogImage,
      Long clickCount,
      Boolean highlighted,
      String content,
      @JsonProperty("protected") Boolean passwordProtected) {

    public static ProfileEntry link(
        ShortCode shortCode,
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
          null,
          false);
    }

    public static ProfileEntry protectedLink(
        ShortCode shortCode,
        String shortUrl,
        String ownerTitle,
        long clickCount,
        boolean highlighted) {
      return new ProfileEntry(
          "LINK",
          null,
          shortCode,
          shortUrl,
          null,
          ownerTitle,
          null,
          clickCount,
          highlighted,
          null,
          true);
    }

    public static ProfileEntry text(Long id, String content) {
      return new ProfileEntry("TEXT", id, null, null, null, null, null, null, null, content, null);
    }

    public static ProfileEntry divider(Long id) {
      return new ProfileEntry("DIVIDER", id, null, null, null, null, null, null, null, null, null);
    }

    public static ProfileEntry image(Long id, String url) {
      return new ProfileEntry("IMAGE", id, null, null, null, null, null, null, null, url, null);
    }

    public static ProfileEntry embed(Long id, String url) {
      return new ProfileEntry("EMBED", id, null, null, null, null, null, null, null, url, null);
    }

    public static ProfileEntry emailForm(Long id, String config) {
      return new ProfileEntry(
          "EMAIL_FORM", id, null, null, null, null, null, null, null, config, null);
    }

    public static ProfileEntry contactCard(Long id, String config) {
      return new ProfileEntry(
          "CONTACT_CARD", id, null, null, null, null, null, null, null, config, null);
    }

    public static ProfileEntry gallery(Long id, String config) {
      return new ProfileEntry(
          "GALLERY", id, null, null, null, null, null, null, null, config, null);
    }

    public static ProfileEntry productCard(Long id, String config, boolean highlighted) {
      return new ProfileEntry(
          "PRODUCT_CARD", id, null, null, null, null, null, null, highlighted, config, null);
    }

    public static ProfileEntry booking(Long id, String config) {
      return new ProfileEntry(
          "BOOKING", id, null, null, null, null, null, null, null, config, null);
    }

    public static ProfileEntry event(Long id, String config, boolean highlighted) {
      return new ProfileEntry(
          "EVENT", id, null, null, null, null, null, null, highlighted, config, null);
    }

    public static ProfileEntry place(Long id, String config) {
      return new ProfileEntry("PLACE", id, null, null, null, null, null, null, null, config, null);
    }
  }
}
