package com.example.short_link.user.api;

import com.example.short_link.user.application.AuthService;
import com.example.short_link.user.application.AuthService.LoginResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<?> devLogin(
      @Valid @RequestBody DevLoginRequest request, HttpServletResponse res) {
    log.warn(
        "dev-login endpoint invoked for email={} — never enable in production", request.email());
    LoginResult result =
        authService.loginWithOAuth(request.email(), DEV_PROVIDER, "dev:" + request.email());
    if (result instanceof LoginResult.TwoFactorRequired challenge) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(new TwoFactorChallengeResponse(challenge.challengeToken()));
    }
    LoginResult.Tokens tokens = (LoginResult.Tokens) result;
    refreshCookieWriter.set(res, tokens.issued().refreshToken());
    return ResponseEntity.ok(new TokenResponse(tokens.issued().accessToken()));
  }

  public record TwoFactorChallengeResponse(String challenge) {}
}
