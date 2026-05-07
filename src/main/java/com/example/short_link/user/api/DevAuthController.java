package com.example.short_link.user.api;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.IssuedTokens;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Profile({"local", "test"})
@Slf4j
public class DevAuthController {

  private static final String DEV_PROVIDER = "dev";

  private final AuthService authService;
  private final RefreshCookieWriter refreshCookieWriter;

  @PostMapping("/dev-login")
  public TokenResponse devLogin(
      @Valid @RequestBody DevLoginRequest request, HttpServletResponse res) {
    log.warn(
        "dev-login endpoint invoked for email={} — never enable in production", request.email());
    IssuedTokens tokens =
        authService.loginWithOAuth(request.email(), DEV_PROVIDER, "dev:" + request.email());
    refreshCookieWriter.set(res, tokens.refreshToken());
    return new TokenResponse(tokens.accessToken());
  }
}
