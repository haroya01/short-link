package com.example.short_link.link.api;

import com.example.short_link.link.application.AnonymousClaimService;
import com.example.short_link.link.application.AnonymousClaimService.ClaimResult;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
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
      @AuthenticationPrincipal Long userId, @RequestBody ClaimRequest request) {
    return service.claim(userId, request.claimTokens());
  }

  public record ClaimRequest(
      @NotEmpty @Size(max = AnonymousClaimService.MAX_TOKENS_PER_REQUEST)
          List<String> claimTokens) {}
}
