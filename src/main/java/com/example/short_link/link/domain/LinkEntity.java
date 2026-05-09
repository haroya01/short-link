package com.example.short_link.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
public class LinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
  private String originalUrl;

  @Column(name = "short_code", nullable = false, length = 16, unique = true)
  private String shortCode;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

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

  public LinkEntity(String originalUrl, String shortCode) {
    this(originalUrl, shortCode, null, null);
  }

  public LinkEntity(String originalUrl, String shortCode, Long userId, Instant expiresAt) {
    this.originalUrl = originalUrl;
    this.shortCode = shortCode;
    this.userId = userId;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
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
