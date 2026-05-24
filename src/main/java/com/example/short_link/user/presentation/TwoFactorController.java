package com.example.short_link.user.presentation;

import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.application.twofactor.TwoFactorService.SetupChallenge;
import com.example.short_link.user.application.twofactor.TwoFactorService.Status;
import com.example.short_link.user.presentation.request.TwoFactorCodeRequest;
import com.example.short_link.user.presentation.response.TwoFactorConfirmResponse;
import com.example.short_link.user.presentation.response.TwoFactorSetupResponse;
import com.example.short_link.user.presentation.response.TwoFactorStatusResponse;
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
  public TwoFactorStatusResponse status(@AuthenticationPrincipal Long userId) {
    Status s = service.status(userId);
    return new TwoFactorStatusResponse(s.enabled(), s.lastUsedAt());
  }

  @PostMapping("/setup")
  public TwoFactorSetupResponse setup(@AuthenticationPrincipal Long userId) {
    SetupChallenge challenge = service.start(userId);
    return new TwoFactorSetupResponse(challenge.secret(), challenge.provisioningUri());
  }

  @PostMapping("/confirm")
  public TwoFactorConfirmResponse confirm(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    List<String> codes = service.confirm(userId, request.code());
    return new TwoFactorConfirmResponse(codes);
  }

  @PostMapping("/disable")
  public void disable(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    service.disable(userId, request.code());
  }

  @PostMapping("/recovery-codes/regenerate")
  public TwoFactorConfirmResponse regenerate(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    List<String> codes = service.regenerateRecoveryCodes(userId, request.code());
    return new TwoFactorConfirmResponse(codes);
  }
}
