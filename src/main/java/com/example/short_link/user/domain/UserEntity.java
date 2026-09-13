package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.user.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
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

  /** SUSPENDED는 만료 전까지 쓰기만 막고, BANNED는 로그인도 막는다. 제재는 UserModerationPort로 전이한다. */
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

  @Column(nullable = false, length = 64)
  private String timezone = "Asia/Seoul";

  /** 서버가 조합하는 푸시의 언어이며, 알 수 없으면 ko를 사용한다. */
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

  /** CDN URL에서 원본 키를 복원할 수 없으므로 재업로드 시 삭제할 키를 별도로 저장한다. */
  @Column(name = "avatar_key", length = 256)
  private String avatarKey;

  @Column(name = "banner_url", length = 512)
  private String bannerUrl;

  @Column(name = "banner_key", length = 256)
  private String bannerKey;

  /** 소셜 프로필 JSON 배열. 검증·정규화는 {@link com.example.short_link.profile.application.Socials}가 담당한다. */
  @Column(name = "socials", length = 1024)
  private String socials;

  /** true면 익명 방문자에게 프로필 집계 통계를 공개한다. 기본값은 비공개다. */
  @Column(name = "is_stats_public", nullable = false)
  private boolean statsPublic = false;

  /** true면 공개 응답에서 팔로워·팔로잉 수를 0으로 표시하지 않고 필드 자체를 생략한다. */
  @Column(name = "hide_follower_count", nullable = false)
  private boolean hideFollowerCount = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "moderation_status", nullable = false, length = 16)
  private ModerationStatus moderationStatus = ModerationStatus.ACTIVE;

  /** SUSPENDED 만료시각 — 이 시각을 지나면 쓰기 게이트가 자동 통과시킨다. BANNED/ACTIVE 면 null. */
  @Column(name = "suspended_until")
  private Instant suspendedUntil;

  /** 가입 시 동의한 약관 버전과 시각. 동의 기록 도입 이전 계정은 null이다. */
  @Column(name = "terms_agreed_at")
  private Instant termsAgreedAt;

  @Column(name = "terms_version", length = 32)
  private String termsVersion;

  public UserEntity(String email, String oauthProvider, String oauthId) {
    this.email = email;
    this.oauthProvider = oauthProvider;
    this.oauthId = oauthId;
    this.role = Role.USER;
    this.timezone = "Asia/Seoul";
  }

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
    String v = theme.trim().toLowerCase(Locale.ROOT);
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

  public void suspend(Instant until) {
    this.moderationStatus = ModerationStatus.SUSPENDED;
    this.suspendedUntil = until;
  }

  public void ban() {
    this.moderationStatus = ModerationStatus.BANNED;
    this.suspendedUntil = null;
  }

  public boolean isBanned() {
    return moderationStatus == ModerationStatus.BANNED;
  }

  public boolean isSuspendedAt(Instant now) {
    return moderationStatus == ModerationStatus.SUSPENDED
        && suspendedUntil != null
        && suspendedUntil.isAfter(now);
  }

  public boolean canWriteAt(Instant now) {
    return !isBanned() && !isSuspendedAt(now);
  }
}
