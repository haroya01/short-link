package com.example.short_link.user.presentation;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.write.AppleIdentityVerifier;
import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.TokenLoginResult;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.request.AppleLoginRequest;
import com.example.short_link.user.presentation.request.TwoFactorVerifyRequest;
import com.example.short_link.user.presentation.response.AppleWebLoginResponse;
import com.example.short_link.user.presentation.response.TokenResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
  private final AppleIdentityVerifier appleVerifier;
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

  /**
   * The web identity token's Services ID audience must be included in {@code
   * short-link.apple.client-ids}. Success sets a refresh cookie; 2FA users receive only a
   * challenge.
   */
  @PostMapping("/apple")
  public AppleWebLoginResponse apple(
      @Valid @RequestBody AppleLoginRequest request, HttpServletResponse res) {
    AppleIdentity identity = appleVerifier.verify(request.identityToken(), request.nonce());
    return switch (authService.loginWithApple(identity.subject(), identity.email())) {
      case TokenLoginResult.Tokens tokens -> {
        refreshCookieWriter.set(res, tokens.issued().refreshToken());
        yield AppleWebLoginResponse.tokens(tokens.issued().accessToken());
      }
      case TokenLoginResult.TwoFactorRequired challenge ->
          AppleWebLoginResponse.twoFactor(challenge.challengeToken());
    };
  }

  @PostMapping("/2fa/verify")
  public TokenResponse verifyTwoFactor(
      @Valid @RequestBody TwoFactorVerifyRequest request, HttpServletResponse res) {
    return issueAndSetCookie(
        authService.completeTwoFactor(request.challenge(), request.code(), request.recovery()),
        res);
  }

  private TokenResponse issueAndSetCookie(IssuedTokens tokens, HttpServletResponse res) {
    refreshCookieWriter.set(res, tokens.refreshToken());
    return TokenResponse.from(tokens);
  }
}
