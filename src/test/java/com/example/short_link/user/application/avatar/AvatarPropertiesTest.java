package com.example.short_link.user.application.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AvatarPropertiesTest {

  @Test
  void zeroOrNegativeTtlFallsBackToDefault() {
    AvatarProperties p = new AvatarProperties("b", "ap-northeast-2", "https://cdn", 0, 0);
    assertThat(p.presignTtlSeconds()).isEqualTo(300);
    assertThat(p.maxBytes()).isEqualTo(5L * 1024 * 1024);
  }

  @Test
  void positiveOverridesAreKept() {
    AvatarProperties p =
        new AvatarProperties("b", "ap-northeast-2", "https://cdn", 600, 10L * 1024 * 1024);
    assertThat(p.presignTtlSeconds()).isEqualTo(600);
    assertThat(p.maxBytes()).isEqualTo(10L * 1024 * 1024);
  }

  @Test
  void isConfiguredRequiresBucketAndRegion() {
    assertThat(new AvatarProperties("b", "r", null, 0, 0).isConfigured()).isTrue();
    assertThat(new AvatarProperties("", "r", null, 0, 0).isConfigured()).isFalse();
    assertThat(new AvatarProperties("b", "", null, 0, 0).isConfigured()).isFalse();
    assertThat(new AvatarProperties(null, "r", null, 0, 0).isConfigured()).isFalse();
    assertThat(new AvatarProperties("b", null, null, 0, 0).isConfigured()).isFalse();
  }
}
