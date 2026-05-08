package com.example.short_link.common.pow;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pow")
@RequiredArgsConstructor
public class PowController {

  private final PowService service;

  @GetMapping("/challenge")
  public ChallengeResponse challenge() {
    PowService.Challenge issued = service.issue();
    return new ChallengeResponse(issued.challenge(), issued.difficulty(), service.isEnforced());
  }

  public record ChallengeResponse(String challenge, int difficulty, boolean enforced) {}
}
