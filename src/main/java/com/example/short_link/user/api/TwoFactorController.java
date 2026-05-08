package com.example.short_link.user.api;

import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.application.twofactor.TwoFactorService.SetupChallenge;
import com.example.short_link.user.application.twofactor.TwoFactorService.Status;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

  private final TwoFactorService service;

  @GetMapping("/status")
  public StatusResponse status(@AuthenticationPrincipal Long userId) {
    Status s = service.status(userId);
    return new StatusResponse(s.enabled(), s.lastUsedAt());
  }

  @PostMapping("/setup")
  public SetupResponse setup(@AuthenticationPrincipal Long userId) {
    SetupChallenge challenge = service.start(userId);
    return new SetupResponse(challenge.secret(), challenge.provisioningUri());
  }

  @PostMapping("/confirm")
  public ConfirmResponse confirm(
      @AuthenticationPrincipal Long userId, @RequestBody CodeRequest request) {
    List<String> codes = service.confirm(userId, request.code());
    return new ConfirmResponse(codes);
  }

  @PostMapping("/disable")
  public void disable(@AuthenticationPrincipal Long userId, @RequestBody CodeRequest request) {
    service.disable(userId, request.code());
  }

  @PostMapping("/recovery-codes/regenerate")
  public ConfirmResponse regenerate(
      @AuthenticationPrincipal Long userId, @RequestBody CodeRequest request) {
    List<String> codes = service.regenerateRecoveryCodes(userId, request.code());
    return new ConfirmResponse(codes);
  }

  public record StatusResponse(boolean enabled, Instant lastUsedAt) {}

  public record SetupResponse(String secret, String provisioningUri) {}

  public record CodeRequest(@NotBlank String code) {}

  public record ConfirmResponse(List<String> recoveryCodes) {}
}
