package com.example.short_link.link.presentation;

import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.application.write.ClaimAnonymousLinksCommand;
import com.example.short_link.link.application.write.ClaimAnonymousLinksUseCase;
import com.example.short_link.link.presentation.request.AnonymousClaimRequest;
import jakarta.validation.Valid;
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

  private final ClaimAnonymousLinksUseCase useCase;

  @PostMapping("/claim-anonymous")
  public ClaimResult claim(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AnonymousClaimRequest request) {
    return useCase.execute(ClaimAnonymousLinksCommand.of(userId, request.claimTokens()));
  }
}
