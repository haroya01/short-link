package com.example.short_link.user.api;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.InvalidRefreshTokenException;
import com.example.short_link.user.application.IssuedTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/refresh")
  public TokenResponse refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse res) {
    if (refreshToken == null) {
      throw new InvalidRefreshTokenException();
    }
    IssuedTokens tokens = authService.refresh(refreshToken);
    setRefreshCookie(res, tokens.refreshToken());
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
    clearRefreshCookie(res);
  }

  private void setRefreshCookie(HttpServletResponse res, String token) {
    Cookie cookie = new Cookie("refresh_token", token);
    cookie.setHttpOnly(true);
    cookie.setPath("/api/v1/auth");
    cookie.setMaxAge((int) Duration.ofDays(14).toSeconds());
    cookie.setSecure(false);
    res.addCookie(cookie);
  }

  private void clearRefreshCookie(HttpServletResponse res) {
    Cookie cookie = new Cookie("refresh_token", "");
    cookie.setHttpOnly(true);
    cookie.setPath("/api/v1/auth");
    cookie.setMaxAge(0);
    cookie.setSecure(false);
    res.addCookie(cookie);
  }
}
