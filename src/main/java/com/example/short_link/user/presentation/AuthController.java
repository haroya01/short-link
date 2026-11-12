package com.example.short_link.user.presentation;

import com.example.short_link.user.application.SessionChannel;
import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.write.AppleIdentityVerifier;
import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.TokenLoginResult;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import com.example.short_link.user.presentation.helper.OperatorMarkerCookie;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.helper.TwoFactorChallengeCookieWriter;
import com.example.short_link.user.presentation.request.AppleLoginRequest;
import com.example.short_link.user.presentation.request.WebTwoFactorVerifyRequest;
import com.example.short_link.user.presentation.response.AppleWebLoginResponse;
import com.example.short_link.user.presentation.response.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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
  private final TwoFactorChallengeCookieWriter challengeCookieWriter;
  private final OperatorMarkerCookie operatorMarkerCookie;

  @PostMapping("/refresh")
  public TokenResponse refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletRequest req,
      HttpServletResponse res) {
    if (refreshToken == null) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    IssuedTokens tokens = authService.refresh(refreshToken, SessionChannel.WEB);
    refreshCookieWriter.set(res, tokens.refreshToken());
    operatorMarkerCookie.onRefresh(req, res, tokens.admin());
    return TokenResponse.from(tokens);
  }

  @PostMapping("/logout")
  public void logout(
      @AuthenticationPrincipal Long userId,
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletRequest req,
      HttpServletResponse res) {
    if (userId != null && refreshToken != null) {
      authService.logout(userId, refreshToken);
    }
    refreshCookieWriter.clear(res);
    operatorMarkerCookie.onLogout(req, res);
  }

  // The web identity token's Services ID audience must be included in short-link.apple.client-ids.
  // Success sets a refresh cookie; 2FA users receive only a challenge.
  @PostMapping("/apple")
  public AppleWebLoginResponse apple(
      @Valid @RequestBody AppleLoginRequest request,
      HttpServletRequest req,
      HttpServletResponse res) {
    AppleIdentity identity = appleVerifier.verify(request.identityToken(), request.nonce());
    return switch (authService.loginWithApple(
        identity.subject(), identity.email(), SessionChannel.WEB)) {
      case TokenLoginResult.Tokens tokens -> {
        setLoginCookies(tokens.issued(), req, res);
        yield AppleWebLoginResponse.tokens(tokens.issued().accessToken());
      }
      case TokenLoginResult.TwoFactorRequired challenge ->
          AppleWebLoginResponse.twoFactor(challenge.challengeToken());
    };
  }

  // A body challenge (Apple web login) wins over a cookie an abandoned OAuth login left behind.
  @PostMapping("/2fa/verify")
  public TokenResponse verifyTwoFactor(
      @Valid @RequestBody WebTwoFactorVerifyRequest request,
      @CookieValue(name = TwoFactorChallengeCookieWriter.COOKIE_NAME, required = false)
          String challengeCookie,
      HttpServletRequest req,
      HttpServletResponse res) {
    String challenge =
        StringUtils.hasText(request.challenge()) ? request.challenge() : challengeCookie;
    if (!StringUtils.hasText(challenge)) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    IssuedTokens tokens =
        authService.completeTwoFactor(
            challenge, request.code(), request.recovery(), SessionChannel.WEB);
    setLoginCookies(tokens, req, res);
    if (challengeCookie != null) {
      challengeCookieWriter.clear(res);
    }
    return TokenResponse.from(tokens);
  }

  private void setLoginCookies(
      IssuedTokens tokens, HttpServletRequest req, HttpServletResponse res) {
    refreshCookieWriter.set(res, tokens.refreshToken());
    operatorMarkerCookie.onLogin(req, res, tokens.admin());
  }
}
