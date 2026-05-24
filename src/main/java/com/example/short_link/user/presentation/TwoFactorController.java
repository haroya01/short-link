package com.example.short_link.user.presentation;

import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.presentation.request.TwoFactorCodeRequest;
import com.example.short_link.user.presentation.response.TwoFactorConfirmResponse;
import com.example.short_link.user.presentation.response.TwoFactorSetupResponse;
import com.example.short_link.user.presentation.response.TwoFactorStatusResponse;
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
    return TwoFactorStatusResponse.from(service.status(userId));
  }

  @PostMapping("/setup")
  public TwoFactorSetupResponse setup(@AuthenticationPrincipal Long userId) {
    return TwoFactorSetupResponse.from(service.start(userId));
  }

  @PostMapping("/confirm")
  public TwoFactorConfirmResponse confirm(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    return TwoFactorConfirmResponse.of(service.confirm(userId, request.code()));
  }

  @PostMapping("/disable")
  public void disable(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    service.disable(userId, request.code());
  }

  @PostMapping("/recovery-codes/regenerate")
  public TwoFactorConfirmResponse regenerate(
      @AuthenticationPrincipal Long userId, @RequestBody TwoFactorCodeRequest request) {
    return TwoFactorConfirmResponse.of(service.regenerateRecoveryCodes(userId, request.code()));
  }
}
