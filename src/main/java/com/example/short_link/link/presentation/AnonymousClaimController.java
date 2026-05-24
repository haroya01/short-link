package com.example.short_link.link.presentation;

import com.example.short_link.link.application.AnonymousClaimService;
import com.example.short_link.link.application.AnonymousClaimService.ClaimResult;
import com.example.short_link.link.presentation.request.AnonymousClaimRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class AnonymousClaimController {

  private final AnonymousClaimService service;

  @PostMapping("/claim-anonymous")
  public ClaimResult claim(
      @AuthenticationPrincipal Long userId, @RequestBody AnonymousClaimRequest request) {
    return service.claim(userId, request.claimTokens());
  }
}
