package com.example.short_link.user.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  private final String frontendBaseUrl;
  private final String mobileRedirectUri;

  public OAuth2LoginFailureHandler(
      @Value("${short-link.frontend-base-url}") String frontendBaseUrl,
      @Value("${short-link.mobile.redirect-uri}") String mobileRedirectUri) {
    this.frontendBaseUrl = frontendBaseUrl;
    this.mobileRedirectUri = mobileRedirectUri;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest req, HttpServletResponse res, AuthenticationException exception)
      throws IOException {
    log.warn("oauth login failed: {}", exception.toString());
    String reason = exception.getClass().getSimpleName();
    String base =
        MobileLoginFlag.consume(req) ? mobileRedirectUri : frontendBaseUrl + "/auth/callback";
    String target = base + "?error=" + URLEncoder.encode(reason, StandardCharsets.UTF_8);
    res.sendRedirect(target);
  }
}
