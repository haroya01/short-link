package com.example.short_link.link.presentation;

import com.example.short_link.common.pow.PowService;
import com.example.short_link.link.presentation.response.ChallengeResponse;
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
}
