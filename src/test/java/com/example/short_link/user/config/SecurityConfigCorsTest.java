package com.example.short_link.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.short_link.user.presentation.security.ApiKeyAuthenticationFilter;
import com.example.short_link.user.presentation.security.JsonAuthenticationEntryPoint;
import com.example.short_link.user.presentation.security.JwtAuthenticationFilter;
import com.example.short_link.user.presentation.security.OAuth2LoginFailureHandler;
import com.example.short_link.user.presentation.security.OAuth2LoginSuccessHandler;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SecurityConfigCorsTest {

  private final SecurityConfig config =
      new SecurityConfig(
          mock(JwtAuthenticationFilter.class),
          mock(ApiKeyAuthenticationFilter.class),
          mock(OAuth2LoginSuccessHandler.class),
          mock(OAuth2LoginFailureHandler.class),
          mock(JsonAuthenticationEntryPoint.class));

  @Test
  void allowsLocalhostInLocalProfile() {
    assertThat(config.corsConfigurationSource("http://localhost:3001", "local")).isNotNull();
  }

  @Test
  void allowsLocalhostWhenNoProfileActive() {
    assertThat(config.corsConfigurationSource("http://localhost:3001", "")).isNotNull();
  }

  @Test
  void allowsValidOriginsInProdProfile() {
    assertThat(config.corsConfigurationSource("https://kurl.me,https://app.kurl.me", "prod"))
        .isNotNull();
  }

  @Test
  void rejectsLocalhostInProdProfile() {
    assertThatThrownBy(() -> config.corsConfigurationSource("http://localhost:3001", "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("localhost");
  }

  @Test
  void rejects127LoopbackInProdProfile() {
    assertThatThrownBy(() -> config.corsConfigurationSource("http://127.0.0.1:3001", "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("127.0.0.1");
  }

  @Test
  void rejectsWildcardInProdProfile() {
    assertThatThrownBy(() -> config.corsConfigurationSource("*", "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("wildcard or localhost");
  }

  @Test
  void rejectsEmptyOriginsInProdProfile() {
    assertThatThrownBy(() -> config.corsConfigurationSource("   ", "prod"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be set");
  }

  @Test
  void rejectsProdProfileMixedWithOtherProfiles() {
    assertThatThrownBy(() -> config.corsConfigurationSource("http://localhost:3001", "local,prod"))
        .isInstanceOf(IllegalStateException.class);
  }

  // RegexRequestMatcher matches against the URL *with* the query string, so the short-code anchor
  // must tolerate a trailing query — otherwise a tracked link (?src=…) or UTM link 401s instead of
  // redirecting. Regression for that production bug.
  @Test
  void shortCodeRegexToleratesTrackingQueryString() {
    Pattern p = Pattern.compile(SecurityConfig.SHORT_CODE_REGEX);
    assertThat(p.matcher("/abc123").matches()).isTrue();
    assertThat(p.matcher("/abc123?src=kakao").matches()).isTrue();
    assertThat(p.matcher("/abc123?utm_source=x&utm_medium=qr").matches()).isTrue();
    // Still scoped to a single alphanumeric segment — must not open real API/authenticated paths.
    assertThat(p.matcher("/api/v1/posts").matches()).isFalse();
    assertThat(p.matcher("/abc/123").matches()).isFalse();
  }

  @Test
  void ogCardRegexToleratesQueryString() {
    Pattern p = Pattern.compile(SecurityConfig.OG_CARD_REGEX);
    assertThat(p.matcher("/abc123/og.png").matches()).isTrue();
    assertThat(p.matcher("/abc123/og.png?v=2").matches()).isTrue();
    assertThat(p.matcher("/abc123/other.png").matches()).isFalse();
  }
}
