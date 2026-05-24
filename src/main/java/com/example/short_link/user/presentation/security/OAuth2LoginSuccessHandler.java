package com.example.short_link.user.presentation.security;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.AuthService.LoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

  @Value("${short-link.frontend-base-url}")
  private String frontendBaseUrl;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest req, HttpServletResponse res, Authentication authentication)
      throws IOException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    String email = principal.getAttribute("email");
    String oauthId = principal.getAttribute("sub");
    String provider =
        ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

    LoginResult result = authService.loginWithOAuth(email, provider, oauthId);

    if (result instanceof LoginResult.TwoFactorRequired challenge) {
      String target =
          frontendBaseUrl
              + "/auth/2fa#challenge="
              + URLEncoder.encode(challenge.challengeToken(), StandardCharsets.UTF_8);
      res.sendRedirect(target);
      return;
    }

    LoginResult.Tokens tokens = (LoginResult.Tokens) result;
    refreshCookieWriter.set(res, tokens.issued().refreshToken());
    String target =
        frontendBaseUrl
            + "/auth/callback#access_token="
            + URLEncoder.encode(tokens.issued().accessToken(), StandardCharsets.UTF_8);
    res.sendRedirect(target);
  }
}
