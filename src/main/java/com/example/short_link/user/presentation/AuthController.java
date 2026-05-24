package com.example.short_link.user.presentation;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import com.example.short_link.user.presentation.request.TwoFactorVerifyRequest;
import com.example.short_link.user.presentation.response.TokenResponse;
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
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    return issueAndSetCookie(authService.refresh(refreshToken), res);
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
    return issueAndSetCookie(
        authService.completeTwoFactor(request.challenge(), request.code(), request.recovery()),
        res);
  }

  private TokenResponse issueAndSetCookie(IssuedTokens tokens, HttpServletResponse res) {
    refreshCookieWriter.set(res, tokens.refreshToken());
    return TokenResponse.from(tokens);
  }
}
