package com.example.short_link.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.AuthService.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandler2FATest {

  @Mock private AuthService authService;
  @Mock private RefreshCookieWriter refreshCookieWriter;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse res;

  private OAuth2LoginSuccessHandler handler;

  @BeforeEach
  void setUp() {
    handler = new OAuth2LoginSuccessHandler(authService, refreshCookieWriter);
    ReflectionTestUtils.setField(handler, "frontendBaseUrl", "http://localhost:3001");
  }

  @Test
  void redirectsTo2FAChallengeWhen2FARequired() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(
            java.util.Set.of(), Map.of("email", "u@x.com", "sub", "g-2fa"), "sub");
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, java.util.Set.of(), "google");
    when(authService.loginWithOAuth("u@x.com", "google", "g-2fa"))
        .thenReturn(new LoginResult.TwoFactorRequired("challenge-token-value"));

    handler.onAuthenticationSuccess(req, res, auth);

    // Refresh cookie 는 2FA 통과 후에만 세팅 — challenge 단계에선 절대 안 됨
    verify(refreshCookieWriter, never())
        .set(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(res).sendRedirect(redirectCaptor.capture());
    String target = redirectCaptor.getValue();
    assertThat(target).startsWith("http://localhost:3001/auth/2fa#challenge=");
    assertThat(target).contains("challenge-token-value");
  }
}
