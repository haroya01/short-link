package com.example.short_link.user.api;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.IssuedTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final AuthService authService;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest req, HttpServletResponse res, Authentication authentication)
      throws IOException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    String email = principal.getAttribute("email");
    String oauthId = principal.getAttribute("sub");
    String provider =
        ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

    IssuedTokens tokens = authService.loginWithOAuth(email, provider, oauthId);

    setRefreshCookie(res, tokens.refreshToken());
    res.setContentType("application/json");
    res.getWriter().write("{\"accessToken\":\"" + tokens.accessToken() + "\"}");
  }

  private void setRefreshCookie(HttpServletResponse res, String token) {
    Cookie cookie = new Cookie("refresh_token", token);
    cookie.setHttpOnly(true);
    cookie.setPath("/api/v1/auth");
    cookie.setMaxAge((int) Duration.ofDays(14).toSeconds());
    cookie.setSecure(false);
    res.addCookie(cookie);
  }
}
