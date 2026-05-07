package com.example.short_link.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2LoginFailureHandlerTest {

  @Test
  void redirectsToFrontendCallbackWithErrorReason() throws Exception {
    OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler("http://localhost:3000");
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = new MockHttpServletResponse();

    handler.onAuthenticationFailure(
        req, res, new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

    assertThat(((MockHttpServletResponse) res).getRedirectedUrl())
        .startsWith("http://localhost:3000/auth/callback?error=");
  }
}
