package com.example.short_link.link.application.dto;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.List;

public final class CachedLinkFixture {

  private final LinkId linkId;
  private final String originalUrl;
  private ShortCode shortCode;
  private Long userId;
  private Instant expiresAt;
  private String ogTitle;
  private String ogDescription;
  private String ogImage;
  private String blockedCountries;
  private boolean passwordRequired;
  private Integer maxViews;
  private String expiredMessage;
  private List<CachedLink.Variant> variants = List.of();
  private CachedLink.VisitOptions visitOptions = CachedLink.VisitOptions.NONE;
  private boolean disabled;

  private CachedLinkFixture(LinkId linkId, String originalUrl) {
    this.linkId = linkId;
    this.originalUrl = originalUrl;
  }

  public static CachedLinkFixture link(LinkId linkId, String originalUrl) {
    return new CachedLinkFixture(linkId, originalUrl);
  }

  public static CachedLink.Variant variant(
      Long id, String url, int weight, boolean enabled, String countryCode) {
    return new CachedLink.Variant(id, url, weight, enabled, countryCode, null, null);
  }

  public CachedLinkFixture shortCode(ShortCode shortCode) {
    this.shortCode = shortCode;
    return this;
  }

  public CachedLinkFixture userId(Long userId) {
    this.userId = userId;
    return this;
  }

  public CachedLinkFixture expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public CachedLinkFixture og(String title, String description, String image) {
    this.ogTitle = title;
    this.ogDescription = description;
    this.ogImage = image;
    return this;
  }

  public CachedLinkFixture blockedCountries(String blockedCountries) {
    this.blockedCountries = blockedCountries;
    return this;
  }

  public CachedLinkFixture passwordRequired(boolean passwordRequired) {
    this.passwordRequired = passwordRequired;
    return this;
  }

  public CachedLinkFixture maxViews(Integer maxViews) {
    this.maxViews = maxViews;
    return this;
  }

  public CachedLinkFixture expiredMessage(String expiredMessage) {
    this.expiredMessage = expiredMessage;
    return this;
  }

  public CachedLinkFixture variants(List<CachedLink.Variant> variants) {
    this.variants = variants;
    return this;
  }

  public CachedLinkFixture visitOptions(CachedLink.VisitOptions visitOptions) {
    this.visitOptions = visitOptions;
    return this;
  }

  public CachedLinkFixture disabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  public CachedLink build() {
    return new CachedLink(
        linkId,
        shortCode,
        userId,
        originalUrl,
        expiresAt,
        ogTitle,
        ogDescription,
        ogImage,
        blockedCountries,
        passwordRequired,
        maxViews,
        expiredMessage,
        variants,
        visitOptions,
        disabled);
  }
}
