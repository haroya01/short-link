package com.example.short_link.user.application.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AppleSignInPropertiesTest {

  @Test
  void defaultsAcceptBothNativeAppBundleIds() {
    AppleSignInProperties props = new AppleSignInProperties(null, null, null);

    // 블로그 앱과 링크 앱은 번들 id 가 다르고 identity token 의 aud 는 각자 자기 번들 id 다.
    // 링크 앱이 목록에서 빠지면 그 앱의 Apple 로그인은 전부 401 — App Review 리젝 재발.
    assertThat(props.clientIds())
        .containsExactlyInAnyOrder("focustime.kurl", "focustime.kurl.links");
    assertThat(props.issuer()).isEqualTo("https://appleid.apple.com");
    assertThat(props.jwkSetUri()).isEqualTo("https://appleid.apple.com/auth/keys");
  }

  @Test
  void explicitClientIdsAreKeptAsGiven() {
    AppleSignInProperties props =
        new AppleSignInProperties(null, null, List.of("focustime.kurl", "me.kurl.signin"));

    assertThat(props.clientIds()).containsExactly("focustime.kurl", "me.kurl.signin");
  }
}
