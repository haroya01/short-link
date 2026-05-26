package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.LoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.request.DevLoginRequest;
import com.example.short_link.user.presentation.response.TokenResponse;
import com.example.short_link.user.presentation.response.TwoFactorChallengeResponse;
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
    return switch (result) {
      case LoginResult.TwoFactorRequired c ->
          ResponseEntity.status(HttpStatus.ACCEPTED)
              .body(new TwoFactorChallengeResponse(c.challengeToken()));
      case LoginResult.Tokens t -> {
        refreshCookieWriter.set(res, t.issued().refreshToken());
        yield ResponseEntity.ok(TokenResponse.from(t.issued()));
      }
    };
  }
}
