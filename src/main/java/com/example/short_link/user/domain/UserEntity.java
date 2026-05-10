package com.example.short_link.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

  public enum Role {
    USER,
    ADMIN
  }

  public enum Tier {
    FREE,
    PRO
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "oauth_provider", nullable = false, length = 32)
  private String oauthProvider;

  @Column(name = "oauth_id", nullable = false)
  private String oauthId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Role role = Role.USER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Tier tier = Tier.FREE;

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Seoul";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(unique = true, length = 32)
  private String username;

  @Column(length = 280)
  private String bio;

  @Column(name = "profile_theme", length = 16)
  private String profileTheme;

  /** Public CDN/S3 URL of the avatar image, or null if user hasn't uploaded one. */
  @Column(name = "avatar_url", length = 512)
  private String avatarUrl;

  /**
   * S3 object key (e.g. avatars/{userId}/{uuid}.jpg). Kept so we can DELETE the old object on
   * re-upload — the URL alone isn't enough to derive the key when CDN domains differ from raw S3.
   */
  @Column(name = "avatar_key", length = 256)
  private String avatarKey;

  /** Public CDN/S3 URL of the profile banner / hero image, or null if not uploaded. */
  @Column(name = "banner_url", length = 512)
  private String bannerUrl;

  /** S3 object key under {@code banners/{userId}/...} — see {@link #avatarKey} note. */
  @Column(name = "banner_key", length = 256)
  private String bannerKey;

  @Column(name = "stripe_customer_id", length = 64)
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id", length = 64)
  private String stripeSubscriptionId;

  @Column(name = "subscription_status", length = 32)
  private String subscriptionStatus;

  @Column(name = "subscription_current_period_end")
  private Instant subscriptionCurrentPeriodEnd;

  public UserEntity(String email, String oauthProvider, String oauthId) {
    this.email = email;
    this.oauthProvider = oauthProvider;
    this.oauthId = oauthId;
    this.role = Role.USER;
    this.tier = Tier.FREE;
    this.timezone = "Asia/Seoul";
  }

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }

  public boolean isPro() {
    return tier == Tier.PRO;
  }

  public void upgradeToPro() {
    this.tier = Tier.PRO;
  }

  public void downgradeToFree() {
    this.tier = Tier.FREE;
  }

  public void linkStripeCustomer(String customerId) {
    this.stripeCustomerId = customerId;
  }

  public void applySubscription(String subscriptionId, String status, Instant currentPeriodEnd) {
    this.stripeSubscriptionId = subscriptionId;
    this.subscriptionStatus = status;
    this.subscriptionCurrentPeriodEnd = currentPeriodEnd;
    if ("active".equals(status) || "trialing".equals(status)) {
      this.tier = Tier.PRO;
    } else {
      this.tier = Tier.FREE;
    }
  }

  public void clearSubscription() {
    this.stripeSubscriptionId = null;
    this.subscriptionStatus = null;
    this.subscriptionCurrentPeriodEnd = null;
    this.tier = Tier.FREE;
  }

  public void promoteToAdmin() {
    this.role = Role.ADMIN;
  }

  public void changeTimezone(String timezone) {
    this.timezone = timezone;
  }

  public void claimUsername(String username) {
    this.username = username;
  }

  public void updateBio(String bio) {
    this.bio = bio;
  }

  public void updateAvatar(String url, String key) {
    this.avatarUrl = url;
    this.avatarKey = key;
  }

  public void updateBanner(String url, String key) {
    this.bannerUrl = url;
    this.bannerKey = key;
  }

  public void updateProfileTheme(String theme) {
    if (theme == null || theme.isBlank()) {
      this.profileTheme = null;
      return;
    }
    String v = theme.trim().toLowerCase();
    switch (v) {
      case "light", "dark", "accent", "sunset", "ocean", "forest", "mono", "neon", "aurora" ->
          this.profileTheme = v;
      default ->
          throw new IllegalArgumentException(
              "theme must be one of: light/dark/accent/sunset/ocean/forest/mono/neon/aurora");
    }
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deletedAt = null;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }
}
