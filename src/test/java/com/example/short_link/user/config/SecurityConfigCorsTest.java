package com.example.short_link.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.short_link.user.presentation.security.ApiKeyAuthenticationFilter;
import com.example.short_link.user.presentation.security.JsonAuthenticationEntryPoint;
import com.example.short_link.user.presentation.security.JwtAuthenticationFilter;
import com.example.short_link.user.presentation.security.OAuth2LoginFailureHandler;
import com.example.short_link.user.presentation.security.OAuth2LoginSuccessHandler;
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
}
