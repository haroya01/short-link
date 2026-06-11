package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.presentation.request.MobileExchangeRequest;
import com.example.short_link.user.presentation.request.MobileRefreshRequest;
import com.example.short_link.user.presentation.request.TwoFactorVerifyRequest;
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
 * Auth surface for the native app. The web flow keeps the refresh token in an HTTP-only cookie; the
 * app holds it in the Keychain instead, so every endpoint here speaks tokens in the request /
 * response body and never touches cookies. Login still goes through the same server-side Google
 * OAuth dance — {@code /start} flags the session as app-initiated and the success handler comes
 * back on the custom scheme with a one-time code that {@code /exchange} redeems for the pair.
 */
@RestController
@RequestMapping("/api/v1/auth/mobile")
@RequiredArgsConstructor
public class MobileAuthController {

  private final AuthService authService;

  @GetMapping("/start")
  public void start(HttpServletRequest req, HttpServletResponse res) throws IOException {
    MobileLoginFlag.mark(req);
    res.sendRedirect("/oauth2/authorization/google");
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
