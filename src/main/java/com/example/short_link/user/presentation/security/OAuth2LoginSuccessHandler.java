package com.example.short_link.user.presentation.security;

import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.MobileLoginResult;
import com.example.short_link.user.application.write.AuthService.TokenLoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final AuthService authService;
  private final RefreshCookieWriter refreshCookieWriter;
  private final String frontendBaseUrl;
  private final String mobileRedirectUri;

  public OAuth2LoginSuccessHandler(
      AuthService authService,
      RefreshCookieWriter refreshCookieWriter,
      @Value("${short-link.frontend-base-url}") String frontendBaseUrl,
      @Value("${short-link.mobile.redirect-uri}") String mobileRedirectUri) {
    this.authService = authService;
    this.refreshCookieWriter = refreshCookieWriter;
    this.frontendBaseUrl = frontendBaseUrl;
    this.mobileRedirectUri = mobileRedirectUri;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest req, HttpServletResponse res, Authentication authentication)
      throws IOException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    String email = principal.getAttribute("email");
    String oauthId = principal.getAttribute("sub");
    String provider =
        ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

    if (MobileLoginFlag.consume(req)) {
      handleMobile(res, authService.loginWithOAuthMobile(email, provider, oauthId));
      return;
    }

    switch (authService.loginWithOAuth(email, provider, oauthId)) {
      case TokenLoginResult.TwoFactorRequired challenge ->
          res.sendRedirect(
              frontendBaseUrl
                  + "/auth/2fa#challenge="
                  + URLEncoder.encode(challenge.challengeToken(), StandardCharsets.UTF_8));
      case TokenLoginResult.Tokens tokens -> {
        refreshCookieWriter.set(res, tokens.issued().refreshToken());
        res.sendRedirect(
            frontendBaseUrl
                + "/auth/callback#access_token="
                + URLEncoder.encode(tokens.issued().accessToken(), StandardCharsets.UTF_8));
      }
    }
  }

  // The app can't read cookies or fragments out of the browser sheet, so both outcomes travel as
  // query params on the custom scheme: a one-time exchange code, or the 2FA challenge token.
  private void handleMobile(HttpServletResponse res, MobileLoginResult result) throws IOException {
    String query =
        switch (result) {
          case MobileLoginResult.TwoFactorRequired challenge ->
              "?challenge=" + URLEncoder.encode(challenge.challengeToken(), StandardCharsets.UTF_8);
          case MobileLoginResult.ExchangeCode code ->
              "?code=" + URLEncoder.encode(code.code(), StandardCharsets.UTF_8);
        };
    res.sendRedirect(mobileRedirectUri + query);
  }
}
