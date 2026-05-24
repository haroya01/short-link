package com.example.short_link.user.api;

import com.example.short_link.user.api.request.TwoFactorVerifyRequest;
import com.example.short_link.user.api.response.TokenResponse;
import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.IssuedTokens;
import com.example.short_link.user.exception.InvalidRefreshTokenException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final RefreshCookieWriter refreshCookieWriter;

  @PostMapping("/refresh")
  public TokenResponse refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse res) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException();
    }
    IssuedTokens tokens = authService.refresh(refreshToken);
    refreshCookieWriter.set(res, tokens.refreshToken());
    return new TokenResponse(tokens.accessToken());
  }

  @PostMapping("/logout")
  public void logout(
      @AuthenticationPrincipal Long userId,
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse res) {
    if (userId != null && refreshToken != null) {
      authService.logout(userId, refreshToken);
    }
    refreshCookieWriter.clear(res);
  }

  @PostMapping("/2fa/verify")
  public TokenResponse verifyTwoFactor(
      @RequestBody TwoFactorVerifyRequest request, HttpServletResponse res) {
    IssuedTokens tokens =
        authService.completeTwoFactor(request.challenge(), request.code(), request.recovery());
    refreshCookieWriter.set(res, tokens.refreshToken());
    return new TokenResponse(tokens.accessToken());
  }
}
