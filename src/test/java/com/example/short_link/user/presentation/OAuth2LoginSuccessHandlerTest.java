package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.AuthService.LoginResult;
import com.example.short_link.user.application.IssuedTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

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
  void redirectsToFrontendCallbackWithTokenInHash() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(java.util.Set.of(), Map.of("email", "u@x.com", "sub", "g-1"), "sub");
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, java.util.Set.of(), "google");
    when(authService.loginWithOAuth("u@x.com", "google", "g-1"))
        .thenReturn(
            new LoginResult.Tokens(new IssuedTokens("access-jwt-value", "refresh-jwt-value")));

    handler.onAuthenticationSuccess(req, res, auth);

    verify(refreshCookieWriter).set(eq(res), eq("refresh-jwt-value"));
    org.mockito.ArgumentCaptor<String> redirectCaptor =
        org.mockito.ArgumentCaptor.forClass(String.class);
    verify(res).sendRedirect(redirectCaptor.capture());
    String target = redirectCaptor.getValue();
    assertThat(target).startsWith("http://localhost:3001/auth/callback#access_token=");
    assertThat(target).contains("access-jwt-value");
  }

  @Test
  void neverWritesAccessTokenInResponseBody() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(java.util.Set.of(), Map.of("email", "u@x.com", "sub", "g-2"), "sub");
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, java.util.Set.of(), "google");
    when(authService.loginWithOAuth(any(), any(), any()))
        .thenReturn(new LoginResult.Tokens(new IssuedTokens("access", "refresh")));

    handler.onAuthenticationSuccess(req, res, auth);

    verify(res, org.mockito.Mockito.never()).getWriter();
    verify(res, org.mockito.Mockito.never()).setContentType("application/json");
  }
}
