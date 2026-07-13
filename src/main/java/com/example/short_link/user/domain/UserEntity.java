package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.user.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseCreatedEntity {

  public enum Role {
    USER,
    ADMIN
  }

  public enum Tier {
    FREE,
    PRO
  }

  /**
   * 관리자 모더레이션 제재 상태. ACTIVE 기본, SUSPENDED 는 {@code suspendedUntil} 까지 쓰기 차단(로그인 허용), BANNED 는
   * 영구(로그인·쓰기 차단). abuse 슬라이스의 {@code UserModerationPort} 로만 전이된다.
   */
  public enum ModerationStatus {
    ACTIVE,
    SUSPENDED,
    BANNED
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

  /** 선호 로케일 — 서버가 조합하는 푸시를 이 언어로 렌더(모르면 ko). {@link #updateLocale}로만 바꾼다. */
  @Column(nullable = false, length = 16)
  private String locale = "ko";

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(unique = true, length = 32)
  private String username;

  @Column(length = 280)
  private String bio;

  @Column(name = "profile_theme", length = 16)
  private String profileTheme;

  @Column(name = "avatar_url", length = 512)
  private String avatarUrl;

  /**
   * S3 object key (e.g. avatars/{userId}/{uuid}.jpg). Kept so we can DELETE the old object on
   * re-upload — the URL alone isn't enough to derive the key when CDN domains differ from raw S3.
   */
  @Column(name = "avatar_key", length = 256)
  private String avatarKey;

  @Column(name = "banner_url", length = 512)
  private String bannerUrl;

  /** S3 object key under {@code banners/{userId}/...} — see {@link #avatarKey} note. */
  @Column(name = "banner_key", length = 256)
  private String bannerKey;

  /**
   * JSON-encoded list of the user's social links shown at the bottom of the public profile, e.g.
   * {@code [{"channel":"x","url":"https://x.com/haroya"},{"channel":"line","url":"..."}]}. Up to 2
   * entries; null/empty means no socials (the visitor sees only Copy + Native share). Parse /
   * validate via {@link com.example.short_link.profile.application.Socials}.
   */
  @Column(name = "socials", length = 1024)
  private String socials;

  /**
   * When true, anonymous visitors can fetch aggregated visit stats for this profile via {@code GET
   * /api/v1/public/profiles/{username}/stats} and the public stats page renders. Default false —
   * opt-in only, owners flip it from the profile edit screen.
   */
  @Column(name = "is_stats_public", nullable = false)
  private boolean statsPublic = false;

  /**
   * When true, the author's follower / following totals are withheld from the public author page —
   * the follow-status response omits both counts entirely (not shown as zero). Default false, so
   * counts are public unless the owner opts out from the profile edit screen.
   */
  @Column(name = "hide_follower_count", nullable = false)
  private boolean hideFollowerCount = false;

  /** 제재 상태 — 관리자 모더레이션에서만 바뀐다. 기본 ACTIVE. */
  @Enumerated(EnumType.STRING)
  @Column(name = "moderation_status", nullable = false, length = 16)
  private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;

  /** SUSPENDED 만료시각 — 이 시각을 지나면 쓰기 게이트가 자동 통과시킨다. BANNED/ACTIVE 면 null. */
  @Column(name = "suspended_until")
  private Instant suspendedUntil;

  /**
   * Which legal terms version the user accepted at sign-up, and when — proof of acceptance for the
   * click-wrap consent shown on the login/sign-up screen. Null for accounts created before consent
   * capture existed (they accepted under the prior browse-wrap notice).
   */
  @Column(name = "terms_agreed_at")
  private Instant termsAgreedAt;

  @Column(name = "terms_version", length = 32)
  private String termsVersion;

  @Embedded private StripeBinding stripe = new StripeBinding();

  // Hibernate maps an @Embedded to null when every column on it is null — for users without any
  // Stripe activity that breaks every downstream `stripe.xxx()` call with NPE. Restore the empty
  // binding after load so callers can treat the field as always-present.
  @PostLoad
  void restoreStripeAfterLoad() {
    if (stripe == null) {
      stripe = new StripeBinding();
    }
  }

  public UserEntity(String email, String oauthProvider, String oauthId) {
    this.email = email;
    this.oauthProvider = oauthProvider;
    this.oauthId = oauthId;
    this.role = Role.USER;
    this.tier = Tier.FREE;
    this.timezone = "Asia/Seoul";
  }

  /** Record acceptance of the legal terms — called once when the account is created at sign-up. */
  private static final java.util.Set<String> SUPPORTED_LOCALES =
      java.util.Set.of("ko", "ja", "en", "vi", "hi");

  /** 지원 로케일로 클램프(기본 ko) — Accept-Language 로 푸시 구독/기기 등록 때 채운다. */
  public void updateLocale(String tag) {
    this.locale = tag != null && SUPPORTED_LOCALES.contains(tag) ? tag : "ko";
  }

  public void recordTermsConsent(String version, Instant at) {
    this.termsVersion = version;
    this.termsAgreedAt = at;
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
    stripe.linkCustomer(customerId);
  }

  public void applySubscription(String subscriptionId, String status, Instant currentPeriodEnd) {
    stripe.applySubscription(subscriptionId, status, currentPeriodEnd);
    if ("active".equals(status) || "trialing".equals(status)) {
      this.tier = Tier.PRO;
    } else {
      this.tier = Tier.FREE;
    }
  }

  public void clearSubscription() {
    stripe.clearSubscription();
    this.tier = Tier.FREE;
  }

  // Backward-compatible accessors for the four columns now living on the embedded StripeBinding.
  public String getStripeCustomerId() {
    return stripe.getStripeCustomerId();
  }

  public String getStripeSubscriptionId() {
    return stripe.getStripeSubscriptionId();
  }

  public String getSubscriptionStatus() {
    return stripe.getSubscriptionStatus();
  }

  public Instant getSubscriptionCurrentPeriodEnd() {
    return stripe.getSubscriptionCurrentPeriodEnd();
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

  public void updateStatsPublic(boolean statsPublic) {
    this.statsPublic = statsPublic;
  }

  public void updateHideFollowerCount(boolean hideFollowerCount) {
    this.hideFollowerCount = hideFollowerCount;
  }

  public void updateAvatar(String url, String key) {
    this.avatarUrl = url;
    this.avatarKey = key;
  }

  public void updateBanner(String url, String key) {
    this.bannerUrl = url;
    this.bannerKey = key;
  }

  /** Pass null or empty string to clear. Validation lives in Socials.normalize. */
  public void updateSocials(String json) {
    this.socials = json;
  }

  public void updateProfileTheme(String theme) {
    if (theme == null || theme.isBlank()) {
      this.profileTheme = null;
      return;
    }
    String v = theme.trim().toLowerCase();
    switch (v) {
      case "light",
              "dark",
              "accent",
              "sunset",
              "ocean",
              "forest",
              "mono",
              "neon",
              "aurora",
              "wave",
              "ember" ->
          this.profileTheme = v;
      default ->
          throw new IllegalArgumentException(
              "theme must be one of: light/dark/accent/sunset/ocean/forest/mono/neon/aurora/wave/ember");
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

  /** 임시 정지 — {@code until} 까지 쓰기 차단. 로그인은 허용한다. */
  public void suspend(Instant until) {
    this.moderationStatus = ModerationStatus.SUSPENDED;
    this.suspendedUntil = until;
  }

  /** 영구 차단 — 로그인·쓰기 모두 차단. 정지 만료시각은 의미가 없으므로 비운다. */
  public void ban() {
    this.moderationStatus = ModerationStatus.BANNED;
    this.suspendedUntil = null;
  }

  public boolean isBanned() {
    return moderationStatus == ModerationStatus.BANNED;
  }

  /** 지금 시점 기준으로 정지 중인지 — 만료시각이 지났으면 정지 아님(자동 해제). */
  public boolean isSuspendedAt(Instant now) {
    return moderationStatus == ModerationStatus.SUSPENDED
        && suspendedUntil != null
        && suspendedUntil.isAfter(now);
  }

  /** 지금 콘텐츠를 생성할 수 있는지 — BANNED 이거나 만료 전 SUSPENDED 면 false. */
  public boolean canWriteAt(Instant now) {
    return !isBanned() && !isSuspendedAt(now);
  }
}
