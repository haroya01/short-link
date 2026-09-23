package com.example.short_link.link.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Use {@link LinkId} outside the JPA boundary; the raw key remains {@code Long} for Hibernate
   * identity generation and persistence caching.
   */
  public LinkId linkId() {
    return id == null ? null : new LinkId(id);
  }

  @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
  private String originalUrl;

  @Column(name = "short_code", nullable = false, length = 16, unique = true)
  private ShortCode shortCode;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "og_title", length = 300)
  private String ogTitle;

  @Column(name = "og_description", length = 800)
  private String ogDescription;

  @Column(name = "og_image", length = 1024)
  private String ogImage;

  @Column(name = "og_fetched_at")
  private Instant ogFetchedAt;

  @Column(name = "og_fetch_status", nullable = false, length = 20)
  private String ogFetchStatus = OgFetchStatus.PENDING.value();

  @Column(name = "og_fetch_attempts", nullable = false)
  private int ogFetchAttempts = 0;

  @Column(name = "stats_public", nullable = false)
  private boolean statsPublic = false;

  @Column(name = "claim_token", length = 32)
  private String claimToken;

  @Column(name = "password_hash", length = 60)
  private String passwordHash;

  @Column(name = "max_views")
  private Integer maxViews;

  @Column(name = "view_count", nullable = false)
  private int viewCount = 0;

  @Column(name = "og_title_override", length = 300)
  private String ogTitleOverride;

  @Column(name = "og_description_override", length = 800)
  private String ogDescriptionOverride;

  @Column(name = "og_image_override", length = 1024)
  private String ogImageOverride;

  @Column(name = "profile_order")
  private Integer profileOrder;

  @Column(name = "profile_highlighted", nullable = false)
  private boolean profileHighlighted = false;

  /** Comma-separated ISO-3166 alpha-2 blocked countries. Null or blank means no blocklist. */
  @Column(name = "blocked_countries", length = 255)
  private String blockedCountries;

  /** Null means not favorited; positions belong to this link owner. */
  @Column(name = "favorite_order")
  private Integer favoriteOrder;

  public void changeFavoriteOrder(Integer order) {
    if (order != null && order < 0) throw new IllegalArgumentException("negative favorite order");
    this.favoriteOrder = order;
  }

  /** Owner-only memo; never shown to visitors. */
  @Column(length = 280)
  private String note;

  /** Overrides the generic copy on the expired or view-limit page when provided. */
  @Column(name = "expired_message", length = 500)
  private String expiredMessage;

  /** When set, expiry serves a 302 to this URL instead of an expired page. */
  @Column(name = "expired_redirect_url", length = 2048)
  private String expiredRedirectUrl;

  public LinkEntity(String originalUrl, String shortCode) {
    this(originalUrl, new ShortCode(shortCode), null, null);
  }

  public LinkEntity(String originalUrl, ShortCode shortCode) {
    this(originalUrl, shortCode, null, null);
  }

  public LinkEntity(String originalUrl, String shortCode, Long userId, Instant expiresAt) {
    this(originalUrl, new ShortCode(shortCode), userId, expiresAt);
  }

  public LinkEntity(String originalUrl, ShortCode shortCode, Long userId, Instant expiresAt) {
    this.originalUrl = originalUrl;
    this.shortCode = shortCode;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && !now.isBefore(expiresAt);
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }

  public void changeOriginalUrl(String originalUrl) {
    this.originalUrl = originalUrl;
    this.ogTitle = null;
    this.ogDescription = null;
    this.ogImage = null;
    this.ogFetchedAt = null;
    this.ogFetchStatus = OgFetchStatus.PENDING.value();
  }

  public void changeExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public void changeExpiredRedirectUrl(String url) {
    this.expiredRedirectUrl = (url == null || url.isBlank()) ? null : url.trim();
  }

  /**
   * Campaign end policy is stored here so redirects do not need campaign lookups. Null values clear
   * existing policy; callers pass {@code expiredMessage} only for EXPIRE, since REDIRECT skips the
   * page.
   */
  public void applyCampaignExpiration(
      Instant expiresAt, String expiredRedirectUrl, String expiredMessage) {
    this.expiresAt = expiresAt;
    this.expiredRedirectUrl =
        (expiredRedirectUrl == null || expiredRedirectUrl.isBlank())
            ? null
            : expiredRedirectUrl.trim();
    this.expiredMessage = LinkText.normalize(expiredMessage, LinkText.EXPIRED_MESSAGE_MAX_LENGTH);
  }

  public void applyOgMetadata(String title, String description, String image, Instant fetchedAt) {
    this.ogTitle = title;
    this.ogDescription = description;
    this.ogImage = image;
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = OgFetchStatus.OK.value();
    this.ogFetchAttempts++;
  }

  public void markOgFetchFailed(Instant fetchedAt, boolean willRetry) {
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = ogFetchFailedStatus(willRetry);
    this.ogFetchAttempts++;
  }

  public static String ogFetchedStatus() {
    return OgFetchStatus.OK.value();
  }

  public static String ogFetchFailedStatus(boolean willRetry) {
    return OgFetchStatus.failure(willRetry).value();
  }

  public void changeStatsVisibility(boolean isPublic) {
    this.statsPublic = isPublic;
  }

  public void setProfileOrder(Integer order) {
    this.profileOrder = order;
  }

  public boolean isOnProfile() {
    return profileOrder != null;
  }

  public void setProfileHighlighted(boolean highlighted) {
    this.profileHighlighted = highlighted;
  }

  public void updateNote(String note) {
    this.note = LinkText.normalize(note, LinkText.NOTE_MAX_LENGTH);
  }

  public void updateExpiredMessage(String message) {
    this.expiredMessage = LinkText.normalize(message, LinkText.EXPIRED_MESSAGE_MAX_LENGTH);
  }

  public void setBlockedCountries(String csv) {
    this.blockedCountries = CountryBlocklist.normalize(csv);
  }

  public boolean isCountryBlocked(String countryCode) {
    return CountryBlocklist.fromCsv(blockedCountries).contains(countryCode);
  }

  public void setClaimToken(String token) {
    this.claimToken = token;
  }

  public void claim(Long newOwnerId) {
    this.userId = newOwnerId;
    this.claimToken = null;
    // Claimed links become permanent because an account can now manage them.
    this.expiresAt = null;
  }

  public boolean hasPassword() {
    return passwordHash != null && !passwordHash.isEmpty();
  }

  public void setPasswordHash(String hash) {
    this.passwordHash = (hash == null || hash.isBlank()) ? null : hash;
  }

  public void setMaxViews(Integer max) {
    this.maxViews = max;
  }

  public void incrementViewCount() {
    this.viewCount++;
  }

  public boolean isViewLimitReached() {
    return maxViews != null && viewCount >= maxViews;
  }

  public void changeOgOverride(String title, String description, String image) {
    this.ogTitleOverride = LinkText.blankToNull(title);
    this.ogDescriptionOverride = LinkText.blankToNull(description);
    this.ogImageOverride = LinkText.blankToNull(image);
  }

  public String getEffectiveOgTitle() {
    return LinkText.firstNonBlank(ogTitleOverride, ogTitle);
  }

  public String getEffectiveOgDescription() {
    return LinkText.firstNonBlank(ogDescriptionOverride, ogDescription);
  }

  public String getEffectiveOgImage() {
    return LinkText.firstNonBlank(ogImageOverride, ogImage);
  }
}
