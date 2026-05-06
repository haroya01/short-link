package com.example.short_link.user.api;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.IssuedTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
  private final RefreshCookieWriter refreshCookieWriter;

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

    refreshCookieWriter.set(res, tokens.refreshToken());
    res.setContentType("application/json");
    res.getWriter().write("{\"accessToken\":\"" + tokens.accessToken() + "\"}");
  }
}
