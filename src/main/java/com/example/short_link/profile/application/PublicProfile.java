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
        Long clickCount,
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
        Long clickCount,
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
      return block("TEXT", id, null, content);
    }

    public static ProfileEntry divider(Long id) {
      return block("DIVIDER", id, null, null);
    }

    public static ProfileEntry image(Long id, String url) {
      return block("IMAGE", id, null, url);
    }

    public static ProfileEntry embed(Long id, String url) {
      return block("EMBED", id, null, url);
    }

    public static ProfileEntry emailForm(Long id, String config) {
      return block("EMAIL_FORM", id, null, config);
    }

    public static ProfileEntry contactCard(Long id, String config) {
      return block("CONTACT_CARD", id, null, config);
    }

    public static ProfileEntry gallery(Long id, String config) {
      return block("GALLERY", id, null, config);
    }

    public static ProfileEntry productCard(Long id, String config, boolean highlighted) {
      return block("PRODUCT_CARD", id, highlighted, config);
    }

    public static ProfileEntry booking(Long id, String config) {
      return block("BOOKING", id, null, config);
    }

    public static ProfileEntry event(Long id, String config, boolean highlighted) {
      return block("EVENT", id, highlighted, config);
    }

    public static ProfileEntry place(Long id, String config) {
      return block("PLACE", id, null, config);
    }

    private static ProfileEntry block(String kind, Long id, Boolean highlighted, String content) {
      return new ProfileEntry(
          kind, id, null, null, null, null, null, null, highlighted, content, null);
    }
  }
}
