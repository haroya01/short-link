package com.example.short_link.link.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.link.domain.repository.*;
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
  private String ogFetchStatus = "PENDING";

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

  /** Marks the one "hero" featured link rendered as a big card on the public profile. */
  @Column(name = "profile_highlighted", nullable = false)
  private boolean profileHighlighted = false;

  /**
   * Comma-separated ISO-3166 alpha-2 country codes blocked from this link. A clicker resolved to
   * any listed country gets a "blocked" page instead of the redirect. Null/blank = no blocklist.
   */
  @Column(name = "blocked_countries", length = 255)
  private String blockedCountries;

  /** Owner-only memo (campaign context, why the link was made). Never shown to visitors. */
  @Column(length = 280)
  private String note;

  /**
   * Optional message rendered on the expired / view-limit page in place of the generic copy. Lets a
   * brand owner say "Sale ended — see the next drop at example.com" instead of a flat "expired".
   */
  @Column(name = "expired_message", length = 500)
  private String expiredMessage;

  /**
   * Optional redirect target applied after the link expires. When non-null, the expired page is
   * skipped and a 302 to this URL is served. Campaign domain pushes the campaign's
   * post-end-destination here on ENDED transition; single-link users may set it directly.
   */
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
    this.ogFetchStatus = "PENDING";
  }

  public void changeExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public void changeExpiredRedirectUrl(String url) {
    this.expiredRedirectUrl = (url == null || url.isBlank()) ? null : url.trim();
  }

  /**
   * Apply a campaign's post-end policy in a single move. Called by the campaign domain when
   * transitioning to ENDED so the redirect hot path can keep reading only this entity.
   *
   * <p>{@code expiredMessage} 는 EXPIRE 분기에서만 의미가 있다 — REDIRECT 일 때는 만료 페이지가 뜨지 않으므로 null 로 전달된다. 인자
   * 자체는 무차별 적용 (null 이면 기존 메시지 클리어, 비어있지 않으면 덮어쓴다) — 호출자가 KEEP/EXPIRE/REDIRECT 분기 의도를 가지고 전달.
   */
  public void applyCampaignExpiration(
      Instant expiresAt, String expiredRedirectUrl, String expiredMessage) {
    this.expiresAt = expiresAt;
    this.expiredRedirectUrl =
        (expiredRedirectUrl == null || expiredRedirectUrl.isBlank())
            ? null
            : expiredRedirectUrl.trim();
    if (expiredMessage == null) {
      this.expiredMessage = null;
    } else {
      String trimmed = expiredMessage.trim();
      this.expiredMessage =
          trimmed.isEmpty() ? null : trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
  }

  public void applyOgMetadata(String title, String description, String image, Instant fetchedAt) {
    this.ogTitle = title;
    this.ogDescription = description;
    this.ogImage = image;
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = "OK";
    this.ogFetchAttempts++;
  }

  public void markOgFetchFailed(Instant fetchedAt, boolean willRetry) {
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = willRetry ? "RETRYABLE" : "ERROR";
    this.ogFetchAttempts++;
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
    if (note == null) {
      this.note = null;
      return;
    }
    String trimmed = note.trim();
    this.note =
        trimmed.isEmpty() ? null : trimmed.length() > 280 ? trimmed.substring(0, 280) : trimmed;
  }

  public void updateExpiredMessage(String message) {
    if (message == null) {
      this.expiredMessage = null;
      return;
    }
    String trimmed = message.trim();
    this.expiredMessage =
        trimmed.isEmpty() ? null : trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
  }

  public void setBlockedCountries(String csv) {
    if (csv == null || csv.isBlank()) {
      this.blockedCountries = null;
      return;
    }
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    for (String raw : csv.split(",")) {
      String code = raw.trim().toUpperCase();
      if (code.length() == 2) seen.add(code);
    }
    this.blockedCountries = seen.isEmpty() ? null : String.join(",", seen);
  }

  public boolean isCountryBlocked(String countryCode) {
    if (blockedCountries == null || countryCode == null) return false;
    String upper = countryCode.toUpperCase();
    for (String code : blockedCountries.split(",")) {
      if (upper.equals(code.trim())) return true;
    }
    return false;
  }

  public void setClaimToken(String token) {
    this.claimToken = token;
  }

  public void claim(Long newOwnerId) {
    this.userId = newOwnerId;
    this.claimToken = null;
    // Anonymous links carry a short TTL so they don't accumulate forever; when the creator signs
    // in we promote them to permanent — they're now under an account that can manage them.
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
    this.ogTitleOverride = blankToNull(title);
    this.ogDescriptionOverride = blankToNull(description);
    this.ogImageOverride = blankToNull(image);
  }

  public String getEffectiveOgTitle() {
    return notBlank(ogTitleOverride) ? ogTitleOverride : ogTitle;
  }

  public String getEffectiveOgDescription() {
    return notBlank(ogDescriptionOverride) ? ogDescriptionOverride : ogDescription;
  }

  public String getEffectiveOgImage() {
    return notBlank(ogImageOverride) ? ogImageOverride : ogImage;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
