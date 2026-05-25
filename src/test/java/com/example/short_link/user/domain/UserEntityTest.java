package com.example.short_link.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.domain.UserEntity.Role;
import com.example.short_link.user.domain.UserEntity.Tier;
import com.example.short_link.user.domain.repository.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserEntityTest {

  private UserEntity newUser() {
    return new UserEntity("u@x.com", "google", "g-1");
  }

  @Test
  void constructorSetsDefaults() {
    UserEntity u = newUser();
    assertThat(u.getEmail()).isEqualTo("u@x.com");
    assertThat(u.getOauthProvider()).isEqualTo("google");
    assertThat(u.getOauthId()).isEqualTo("g-1");
    assertThat(u.getRole()).isEqualTo(Role.USER);
    assertThat(u.getTier()).isEqualTo(Tier.FREE);
    assertThat(u.getTimezone()).isEqualTo("Asia/Seoul");
    assertThat(u.isAdmin()).isFalse();
    assertThat(u.isPro()).isFalse();
    assertThat(u.isDeleted()).isFalse();
    assertThat(u.isStatsPublic()).isFalse();
  }

  @Test
  void promoteToAdminFlipsRole() {
    UserEntity u = newUser();
    u.promoteToAdmin();
    assertThat(u.isAdmin()).isTrue();
  }

  @Test
  void upgradeAndDowngradeTier() {
    UserEntity u = newUser();
    u.upgradeToPro();
    assertThat(u.isPro()).isTrue();
    u.downgradeToFree();
    assertThat(u.isPro()).isFalse();
  }

  @Test
  void linkStripeCustomerAndApplyActiveSubscriptionFlipsToPro() {
    UserEntity u = newUser();
    u.linkStripeCustomer("cus_abc");
    assertThat(u.getStripeCustomerId()).isEqualTo("cus_abc");
    Instant end = Instant.parse("2030-01-01T00:00:00Z");
    u.applySubscription("sub_xyz", "active", end);
    assertThat(u.getStripeSubscriptionId()).isEqualTo("sub_xyz");
    assertThat(u.getSubscriptionStatus()).isEqualTo("active");
    assertThat(u.getSubscriptionCurrentPeriodEnd()).isEqualTo(end);
    assertThat(u.isPro()).isTrue();
  }

  @Test
  void trialingAlsoCountsAsPro() {
    UserEntity u = newUser();
    u.applySubscription("sub", "trialing", null);
    assertThat(u.isPro()).isTrue();
  }

  @Test
  void canceledSubscriptionRevertsToFree() {
    UserEntity u = newUser();
    u.applySubscription("sub", "active", null);
    u.applySubscription("sub", "canceled", null);
    assertThat(u.isPro()).isFalse();
  }

  @Test
  void clearSubscriptionWipesFields() {
    UserEntity u = newUser();
    u.applySubscription("sub", "active", Instant.now());
    u.clearSubscription();
    assertThat(u.getStripeSubscriptionId()).isNull();
    assertThat(u.getSubscriptionStatus()).isNull();
    assertThat(u.getSubscriptionCurrentPeriodEnd()).isNull();
    assertThat(u.isPro()).isFalse();
  }

  @Test
  void usernameAndBioUpdaters() {
    UserEntity u = newUser();
    u.claimUsername("alice");
    u.updateBio("hi");
    assertThat(u.getUsername()).isEqualTo("alice");
    assertThat(u.getBio()).isEqualTo("hi");
  }

  @Test
  void avatarAndBannerUpdaters() {
    UserEntity u = newUser();
    u.updateAvatar("https://cdn/a.png", "avatars/1/a.png");
    u.updateBanner("https://cdn/b.png", "banners/1/b.png");
    assertThat(u.getAvatarUrl()).isEqualTo("https://cdn/a.png");
    assertThat(u.getAvatarKey()).isEqualTo("avatars/1/a.png");
    assertThat(u.getBannerUrl()).isEqualTo("https://cdn/b.png");
    assertThat(u.getBannerKey()).isEqualTo("banners/1/b.png");
  }

  @Test
  void socialsUpdater() {
    UserEntity u = newUser();
    u.updateSocials("[]");
    assertThat(u.getSocials()).isEqualTo("[]");
  }

  @Test
  void statsPublicToggle() {
    UserEntity u = newUser();
    u.updateStatsPublic(true);
    assertThat(u.isStatsPublic()).isTrue();
    u.updateStatsPublic(false);
    assertThat(u.isStatsPublic()).isFalse();
  }

  @Test
  void timezoneChange() {
    UserEntity u = newUser();
    u.changeTimezone("UTC");
    assertThat(u.getTimezone()).isEqualTo("UTC");
  }

  @Test
  void softDeleteAndRestore() {
    UserEntity u = newUser();
    u.softDelete();
    assertThat(u.isDeleted()).isTrue();
    u.restore();
    assertThat(u.isDeleted()).isFalse();
  }

  @Test
  void profileThemeAcceptsKnownValues() {
    UserEntity u = newUser();
    for (String t :
        new String[] {
          "light", "dark", "accent", "sunset", "ocean", "forest", "mono", "neon", "aurora", "wave",
          "ember"
        }) {
      u.updateProfileTheme(t);
      assertThat(u.getProfileTheme()).isEqualTo(t);
    }
  }

  @Test
  void profileThemeNullOrBlankClears() {
    UserEntity u = newUser();
    u.updateProfileTheme("dark");
    u.updateProfileTheme(null);
    assertThat(u.getProfileTheme()).isNull();
    u.updateProfileTheme("dark");
    u.updateProfileTheme("   ");
    assertThat(u.getProfileTheme()).isNull();
  }

  @Test
  void profileThemeRejectsUnknown() {
    UserEntity u = newUser();
    assertThatThrownBy(() -> u.updateProfileTheme("rainbow"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void profileThemeNormalizesCase() {
    UserEntity u = newUser();
    u.updateProfileTheme("  DARK  ");
    assertThat(u.getProfileTheme()).isEqualTo("dark");
  }
}
