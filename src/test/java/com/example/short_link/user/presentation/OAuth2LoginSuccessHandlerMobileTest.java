package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.LoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.security.MobileLoginFlag;
import com.example.short_link.user.presentation.security.OAuth2LoginSuccessHandler;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerMobileTest {

  @Mock private AuthService authService;
  @Mock private RefreshCookieWriter refreshCookieWriter;

  private OAuth2LoginSuccessHandler handler;
  private MockHttpServletRequest req;
  private MockHttpServletResponse res;
  private OAuth2AuthenticationToken auth;

  @BeforeEach
  void setUp() {
    handler =
        new OAuth2LoginSuccessHandler(
            authService, refreshCookieWriter, "http://localhost:3001", "kurl://auth");
    req = new MockHttpServletRequest();
    MobileLoginFlag.mark(req);
    res = new MockHttpServletResponse();
    DefaultOAuth2User principal =
        new DefaultOAuth2User(Set.of(), Map.of("email", "u@x.com", "sub", "g-1"), "sub");
    auth = new OAuth2AuthenticationToken(principal, Set.of(), "google");
  }

  @Test
  void mobileLoginRedirectsToCustomSchemeWithExchangeCode() throws Exception {
    when(authService.loginWithOAuthMobile("u@x.com", "google", "g-1"))
        .thenReturn(new LoginResult.MobileExchangeCode("code-123"));

    handler.onAuthenticationSuccess(req, res, auth);

    assertThat(res.getRedirectedUrl()).isEqualTo("kurl://auth?code=code-123");
    // No browser session on mobile — the pair only ever leaves through /exchange.
    verifyNoInteractions(refreshCookieWriter);
  }

  @Test
  void mobileTwoFactorRedirectsWithChallenge() throws Exception {
    when(authService.loginWithOAuthMobile("u@x.com", "google", "g-1"))
        .thenReturn(new LoginResult.TwoFactorRequired("challenge-jwt"));

    handler.onAuthenticationSuccess(req, res, auth);

    assertThat(res.getRedirectedUrl()).isEqualTo("kurl://auth?challenge=challenge-jwt");
    verifyNoInteractions(refreshCookieWriter);
  }

  @Test
  void flagIsConsumedSoNextLoginFallsBackToWeb() throws Exception {
    when(authService.loginWithOAuthMobile("u@x.com", "google", "g-1"))
        .thenReturn(new LoginResult.MobileExchangeCode("code-123"));

    handler.onAuthenticationSuccess(req, res, auth);

    assertThat(MobileLoginFlag.consume(req)).isFalse();
  }
}
