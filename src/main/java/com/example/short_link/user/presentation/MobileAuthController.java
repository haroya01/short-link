package com.example.short_link.user.presentation;

import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.write.AppleIdentityVerifier;
import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.presentation.request.AppleLoginRequest;
import com.example.short_link.user.presentation.request.MobileExchangeRequest;
import com.example.short_link.user.presentation.request.MobileRefreshRequest;
import com.example.short_link.user.presentation.request.TwoFactorVerifyRequest;
import com.example.short_link.user.presentation.response.AppleLoginResponse;
import com.example.short_link.user.presentation.response.MobileTokenResponse;
import com.example.short_link.user.presentation.security.MobileLoginFlag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile refresh tokens travel in request/response bodies for app storage. Browser OAuth returns a
 * one-time custom-scheme code, redeemed at /exchange for the token pair.
 */
@RestController
@RequestMapping("/api/v1/auth/mobile")
@RequiredArgsConstructor
public class MobileAuthController {

  private final AuthService authService;
  private final AppleIdentityVerifier appleVerifier;

  @GetMapping("/start")
  public void start(HttpServletRequest req, HttpServletResponse res) throws IOException {
    MobileLoginFlag.mark(req);
    res.sendRedirect("/oauth2/authorization/google");
  }

  @PostMapping("/apple")
  public AppleLoginResponse apple(@Valid @RequestBody AppleLoginRequest request) {
    AppleIdentity identity = appleVerifier.verify(request.identityToken(), request.nonce());
    return switch (authService.loginWithApple(identity.subject(), identity.email())) {
      case AuthService.TokenLoginResult.Tokens tokens -> AppleLoginResponse.tokens(tokens.issued());
      case AuthService.TokenLoginResult.TwoFactorRequired challenge ->
          AppleLoginResponse.twoFactor(challenge.challengeToken());
    };
  }

  @PostMapping("/exchange")
  public MobileTokenResponse exchange(@Valid @RequestBody MobileExchangeRequest request) {
    return MobileTokenResponse.from(authService.exchangeMobileCode(request.code()));
  }

  @PostMapping("/refresh")
  public MobileTokenResponse refresh(@Valid @RequestBody MobileRefreshRequest request) {
    return MobileTokenResponse.from(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/2fa/verify")
  public MobileTokenResponse verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
    return MobileTokenResponse.from(
        authService.completeTwoFactor(request.challenge(), request.code(), request.recovery()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody MobileRefreshRequest request) {
    authService.logout(request.refreshToken());
  }
}
