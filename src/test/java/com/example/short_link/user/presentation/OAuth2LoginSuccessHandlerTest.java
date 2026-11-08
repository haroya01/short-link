package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.TokenLoginResult;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.helper.TwoFactorChallengeCookieWriter;
import com.example.short_link.user.presentation.security.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

  @Mock private AuthService authService;
  @Mock private RefreshCookieWriter refreshCookieWriter;
  @Mock private TwoFactorChallengeCookieWriter challengeCookieWriter;
  @Mock private HttpServletRequest req;
  @Mock private HttpServletResponse res;

  private OAuth2LoginSuccessHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new OAuth2LoginSuccessHandler(
            authService,
            refreshCookieWriter,
            challengeCookieWriter,
            "http://localhost:3001",
            "kurl://auth");
  }

  @Test
  void redirectsToFrontendCallbackWithNoTokenInTheUrl() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(Set.of(), Map.of("email", "u@x.com", "sub", "g-1"), "sub");
    OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Set.of(), "google");
    when(authService.loginWithOAuth("u@x.com", "google", "g-1"))
        .thenReturn(
            new TokenLoginResult.Tokens(new IssuedTokens("access-jwt-value", "refresh-jwt-value")));

    handler.onAuthenticationSuccess(req, res, auth);

    verify(refreshCookieWriter).set(eq(res), eq("refresh-jwt-value"));
    ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
    verify(res).sendRedirect(redirectCaptor.capture());
    assertThat(redirectCaptor.getValue()).isEqualTo("http://localhost:3001/auth/callback");
  }

  @Test
  void neverWritesAccessTokenInResponseBody() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(Set.of(), Map.of("email", "u@x.com", "sub", "g-2"), "sub");
    OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(principal, Set.of(), "google");
    when(authService.loginWithOAuth(any(), any(), any()))
        .thenReturn(new TokenLoginResult.Tokens(new IssuedTokens("access", "refresh")));

    handler.onAuthenticationSuccess(req, res, auth);

    verify(res, Mockito.never()).getWriter();
    verify(res, Mockito.never()).setContentType("application/json");
  }

  @Test
  void aBannedAccountIsSentBackToTheWebCallbackWithTheReason() throws Exception {
    when(authService.loginWithOAuth("u@x.com", "google", "g-3"))
        .thenThrow(new UserException(UserErrorCode.ACCOUNT_BANNED));

    handler.onAuthenticationSuccess(req, res, googleLogin("g-3"));

    verify(res).sendRedirect("http://localhost:3001/auth/callback?error=ACCOUNT_BANNED");
  }

  @Test
  void anAccountThatCannotBeCreatedIsSentBackToTheAppWithAnError() throws Exception {
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);
    when(session.getAttribute("short-link.mobile-oauth-login")).thenReturn(Boolean.TRUE);
    when(authService.loginWithOAuthMobile("u@x.com", "google", "g-4"))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry for key uk_users_email"));

    handler.onAuthenticationSuccess(req, res, googleLogin("g-4"));

    verify(res).sendRedirect("kurl://auth?error=LOGIN_FAILED");
  }

  private static OAuth2AuthenticationToken googleLogin(String sub) {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(Set.of(), Map.of("email", "u@x.com", "sub", sub), "sub");
    return new OAuth2AuthenticationToken(principal, Set.of(), "google");
  }
}
